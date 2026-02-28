"""
独立的消息队列处理进程
负责从任务队列获取任务，调度解析任务，并将结果发送到结果队列
"""
import time
import signal
import sys
import asyncio
import json
from datetime import datetime, timezone
from typing import Dict, Any
from kparser.common.log_utils import get_logger
from kparser.common import config as settings
from kparser.core.job_manager import job_manager
from kparser.core.process_executor import run_in_process
from kparser.core import loader_dispatch as loader_api
from kparser.rabbitmq_queue.consumer import TaskQueueConsumer
from kparser.rabbitmq_queue.producer import TaskResultProducer
from kparser.config.doc_type_loader import validate_and_get_params

logger = get_logger(__name__)


class QueueProcessor:
    """消息队列处理器 - 独立进程运行"""
    
    def __init__(self):
        self.consumer = None
        self.producer = None
        self.running = False
        self.max_workers = int(settings.SERVICE.get("max_job_number", 32))
        
    def _setup_signal_handlers(self):
        """设置信号处理器"""
        def signal_handler(signum, frame):
            logger.info(f"⚠️  Received signal {signum}, shutting down gracefully...")
            self.stop()
            sys.exit(0)
        
        signal.signal(signal.SIGTERM, signal_handler)
        signal.signal(signal.SIGINT, signal_handler)
    
    def _parse_task_params(self, message: Dict[str, Any]) -> tuple[Dict[str, Any], str]:
        """
        解析任务参数，将消息格式转换为解析器所需的格式
        
        Args:
            message: 从队列接收的消息
        
        Returns:
            tuple: (解析器参数字典, 错误信息)
            如果成功返回 (parser_params, None)
            如果失败返回 (None, error_message)
        """
        params = message.get('params', {})
        task_action = message.get('taskAction')
        task_id = message.get('taskId')
        
        # 基础参数
        parser_params = {
            'task_id': task_id,
            'request_id': task_id,  # 使用 taskId 作为 request_id
            'filename': params.get('taskName', 'unknown'),
            'original_url': params.get('originalUrl', ''),
            'doc_id': task_id,  # 使用 taskId 作为 doc_id
        }
        
        doc_type = params.get('docType')
        val = params.get("enable_vision_understand", False)
        logger.info(f"enable_vision_understand={type(val)}, val={val}")
        if isinstance(val, str):
            enable_vision_understand = val.lower() == "true"
        else:
            enable_vision_understand = val

        
        if doc_type:
            # 🔥 使用 doc_type 加载配置参数（与 multi_instance_app.py 逻辑一致）
            is_valid, parse_params, error_msg = validate_and_get_params(doc_type)
            
            if not is_valid:
                logger.error(f"Invalid doc_type: {doc_type}, error: {error_msg}")
                return None, f"doc_type 无效: {error_msg}"
            
            logger.info(f"使用文档类型配置: {doc_type}, 参数: {parse_params}")
            
            # 从配置中提取解析参数
            upload_image = parse_params.get("upload_image", True)
            table_image = parse_params.get("table_image", True)
            parser_rule_data = parse_params.get("parser_rule", [{"rule_method":"3","feature_value":["ROW_HEADER"]}])
            to_pdf = parse_params.get("to_pdf", False)
            layout = parse_params.get("layout", True)
            ocr_content = parse_params.get("ocr_content", True)
            vision = parse_params.get("vision", False)
            table_vision = parse_params.get("table_vision", False)
            start_page = parse_params.get("start_page", 1)
            end_page = parse_params.get("end_page", "")
            
            # 处理 parser_rule（可能是列表或字符串）
            if isinstance(parser_rule_data, list):
                rule_config_dict = parser_rule_data
            elif isinstance(parser_rule_data, str):
                try:
                    rule_config_dict = json.loads(parser_rule_data) if parser_rule_data else []
                except json.JSONDecodeError:
                    rule_config_dict = [{"rule_method":"3","feature_value":["ROW_HEADER"]}]
            else:
                rule_config_dict = [{"rule_method":"3","feature_value":["ROW_HEADER"]}]
            
        else:
            # 没有 doc_type，使用默认参数
            logger.info(f"未指定 doc_type，使用默认解析参数")
            upload_image = True
            table_image = True
            rule_config_dict = [{"rule_method": "3", "feature_value": ["ROW_HEADER"]}]
            to_pdf = False
            layout = True
            # 根据 taskAction 设置 ocr_content
            ocr_content = True if task_action == 'ocrRecognition' else False
            vision = False
            table_vision = False
            start_page = 1
            end_page = ""
        
        # 更新解析参数
        parser_params.update({
            'from_page': start_page - 1,  # 转换为 0 基索引
            'to_page': end_page if end_page else None,
            'upload_image': upload_image,
            'table_image': table_image,
            'rule_config': rule_config_dict,
            'to_pdf': to_pdf,
            'layout': layout,
            'ocr_content': ocr_content,
            'vision': vision,
            'table_vision': table_vision,
            'environment': params.get('environment', 'ONLINE'),
            'oss_type': 'TOS',
            'oss_config': {},
            'call_back_url': '',
            'enable_vision_understand': enable_vision_understand
        })
        
        # 🔥 注意：doc_type 不传给 parse_file_deepdoc，只用于获取配置
        
        return parser_params, None
    
    async def _process_task(self, message: Dict[str, Any]) -> bool:
        """
        处理单个任务
        
        Args:
            message: 任务消息
        
        Returns:
            是否成功接受任务
        """
        task_id = message.get('taskId')
        task_action = message.get('taskAction')
        
        try:
            # 检查是否有空余进程
            can_accept, current_load, max_workers = await job_manager.can_accept_new_job()
            
            if not can_accept:
                logger.warning(f"⚠️  No available workers ({current_load}/{max_workers}), rejecting task: {task_id}")
                return False
            
            # 发送任务开始消息
            self.producer.send_task_start(task_id, f"开始处理{task_action}任务")
            
            # 创建任务
            await job_manager.create_job(
                task_id,
                filename=message.get('params', {}).get('taskName', 'unknown'),
                task_action=task_action
            )
            
            # 解析任务参数
            parser_params, error_msg = self._parse_task_params(message)
            
            if error_msg:
                # 参数解析失败，发送失败消息
                logger.error(f"❌ Failed to parse task params for {task_id}: {error_msg}")
                self.producer.send_task_failure(task_id, error_msg, f"参数解析失败: {error_msg}")
                return False
            
            logger.info(f"🚀 Starting task: taskId={task_id}, action={task_action}, current_load={current_load + 1}/{max_workers}")
            
            # 启动后台任务处理
            asyncio.create_task(self._run_parse_task(task_id, parser_params))
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Error processing task {task_id}: {e}")
            # 发送失败消息
            self.producer.send_task_failure(task_id, str(e), f"任务处理异常: {e}")
            return False
    
    async def _run_parse_task(self, task_id: str, parser_params: Dict[str, Any]):
        """
        运行解析任务并发送结果
        """
        try:
            # 更新进度
            self.producer.send_task_progress(task_id, 10, "正在解析文档")
            
            # 执行解析（在进程池中运行）
            result = await run_in_process(loader_api.parse_file_deepdoc, **parser_params)
            
            # 更新进度
            self.producer.send_task_progress(task_id, 90, "解析完成，准备返回结果")
            
            # 检查任务是否已被标记为失败
            if task_id in job_manager.jobs and job_manager.jobs[task_id]["status"] == "failed":
                error_msg = job_manager.jobs[task_id].get("result", "任务执行失败")
                logger.info(f"Task {task_id} already marked as failed, sending error to queue")
                self.producer.send_task_failure(task_id, str(error_msg))
                return
            
            # 检查结果是否有效（支持新的字典格式和旧的字符串格式）
            oss_url = None
            pdf_file = None
            
            # 兼容新旧两种返回格式
            if isinstance(result, dict):
                # 新格式：字典 {"oss_url": "...", "pdf_file": "..."}
                oss_url = result.get("oss_url")
                pdf_file = result.get("pdf_file", "")
            elif isinstance(result, str):
                # 旧格式：字符串（向后兼容）
                oss_url = result
                pdf_file = ""
            
            if oss_url is not None and "json" in oss_url:
                # 更新任务状态
                job_manager.jobs[task_id]["result"] = oss_url  # 解析结果 URL
                job_manager.jobs[task_id]["pdf_file"] = pdf_file  # PDF 文件下载链接
                job_manager.jobs[task_id]["status"] = "complete"
                job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)
                
                logger.info(f"Task {task_id} completed. oss_url={oss_url}, pdf_file={pdf_file}")
                
                # 发送成功消息
                self.producer.send_task_success(
                    task_id,
                    result={
                        'resultUrl': oss_url,  # 解析结果 JSON URL
                        'pdfFile': pdf_file,  # PDF 文件下载链接
                        'filename': parser_params.get('filename')
                    },
                    message="文档解析成功"
                )
                
                logger.info(f"✅ Task {task_id} completed successfully, result sent to queue")
            else:
                error_msg = f"解析结果无效: {result}"
                logger.error(f"❌ Task {task_id} result invalid: {result}")
                
                # 更新任务状态为失败
                job_manager.jobs[task_id]["status"] = "failed"
                job_manager.jobs[task_id]["result"] = error_msg
                job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)
                
                # 发送失败消息
                self.producer.send_task_failure(task_id, error_msg)
                
        except Exception as e:
            logger.error(f"❌ Error running parse task {task_id}: {e}")
            
            # 更新任务状态为失败
            if task_id in job_manager.jobs:
                job_manager.jobs[task_id]["status"] = "failed"
                job_manager.jobs[task_id]["result"] = str(e)
                job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)
            
            # 发送失败消息
            self.producer.send_task_failure(task_id, str(e))
    
    def _message_callback(self, message: Dict[str, Any]) -> bool:
        """
        消息回调（同步版本，用于消费者）
        
        Returns:
            是否成功接受任务
        """
        # 创建新的事件循环来运行异步任务
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            return loop.run_until_complete(self._process_task(message))
        finally:
            loop.close()
    
    async def _poll_messages(self):
        """
        定期轮询消息（非阻塞模式）
        当有空余进程时主动获取任务
        """
        logger.info("🔄 Starting message polling mode...")
        
        while self.running:
            try:
                # 检查是否有空余进程
                can_accept, current_load, max_workers = await job_manager.can_accept_new_job()
                
                if can_accept:
                    # 尝试获取一个消息
                    msg_data = self.consumer.get_single_message()
                    
                    if msg_data:
                        message = msg_data['message']
                        delivery_tag = msg_data['delivery_tag']
                        task_id = message.get('taskId')
                        
                        logger.info(f"📨 Polled message: taskId={task_id}")
                        
                        # 处理任务
                        success = await self._process_task(message)
                        
                        if success:
                            # 确认消息
                            self.consumer.ack_message(delivery_tag)
                        else:
                            # 拒绝消息（不重新入队）
                            self.consumer.reject_message(delivery_tag, requeue=False)
                    else:
                        # 没有消息，短暂休眠
                        await asyncio.sleep(1)
                else:
                    # 没有空余进程，等待
                    await asyncio.sleep(2)
                    
            except Exception as e:
                logger.error(f"❌ Error in polling loop: {e}")
                await asyncio.sleep(5)
    
    def start(self):
        """启动队列处理器"""
        logger.info("=" * 80)
        logger.info("🚀 Starting Queue Processor")
        logger.info(f"   Max Workers: {self.max_workers}")
        logger.info(f"   Supported Actions: documentParse, ocrRecognition")
        logger.info("=" * 80)
        
        self.running = True
        self._setup_signal_handlers()
        
        try:
            # 初始化消费者和生产者
            self.consumer = TaskQueueConsumer()
            self.producer = TaskResultProducer()
            
            logger.info("✅ RabbitMQ consumer and producer initialized")
            
            # 使用轮询模式（非阻塞）
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            
            try:
                loop.run_until_complete(self._poll_messages())
            finally:
                loop.close()
                
        except Exception as e:
            logger.error(f"❌ Queue processor error: {e}")
            raise
        finally:
            self.stop()
    
    def stop(self):
        """停止队列处理器"""
        logger.info("🛑 Stopping Queue Processor...")
        self.running = False
        
        if self.consumer:
            self.consumer.close()
        if self.producer:
            self.producer.close()
        
        logger.info("✅ Queue Processor stopped")


def main():
    """主入口"""
    processor = QueueProcessor()
    processor.start()


if __name__ == '__main__':
    main()

