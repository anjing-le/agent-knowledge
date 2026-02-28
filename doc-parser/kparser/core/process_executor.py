import asyncio
import multiprocessing
import os
from datetime import datetime, timezone

from kparser.common import config as settings
from kparser.core.job_manager import job_manager
from kparser.core.utils import job_fail_wrap
from kparser.core import loader_dispatch as loader_api
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)

# 🔧 修复 SIGSEGV：设置多进程启动方法为 'spawn'
# 在使用 OpenCV、ONNX、PyMuPDF 等 C 扩展库时，fork 方法不安全
# 必须使用 spawn 方法避免段错误
try:
    multiprocessing.set_start_method('spawn', force=True)
    logger.info("✅ Multiprocessing start method set to 'spawn' to prevent SIGSEGV")
except RuntimeError:
    # 如果已经设置过，忽略错误
    logger.warning("⚠️ Multiprocessing start method already set")


def cleanup_zombie_processes():
    """
    清理僵尸进程
    
    在Unix系统上，调用waitpid(-1, WNOHANG)来回收所有已终止的子进程。
    这个函数应该在每次任务完成后调用，或者定期调用（如每60秒）。
    
    僵尸进程是已经终止但父进程尚未调用wait()的进程，它们会占用进程表槽位。
    """
    try:
        cleaned_count = 0
        while True:
            try:
                # 非阻塞方式回收僵尸进程
                # -1 表示等待任何子进程
                # os.WNOHANG 表示非阻塞（如果没有僵尸进程立即返回）
                pid, status = os.waitpid(-1, os.WNOHANG)
                if pid == 0:
                    # 没有更多僵尸进程
                    break
                cleaned_count += 1
                logger.debug(f"✅ Cleaned up zombie process PID={pid}, exit_status={status}")
            except ChildProcessError:
                # 没有子进程需要回收
                break
            except OSError as e:
                logger.warning(f"Error cleaning zombie processes: {e}")
                break
        
        if cleaned_count > 0:
            logger.info(f"✅ Cleaned up {cleaned_count} zombie process(es)")
    except Exception as e:
        logger.error(f"Failed to cleanup zombie processes: {e}")


# 使用 multiprocessing.Process 直接创建子进程
def process_wrapper(fn, queue, *args, **kwargs):
    """包装函数，将结果或异常放入队列"""
    try:
        result = fn(*args, **kwargs)
        queue.put(result)
    except Exception as e:
        queue.put(e)


async def run_in_process(fn, *args, **kwargs):
    """在进程池中异步运行函数，并添加超时控制，确保超时后终止任务"""
    timeout = int(settings.SERVICE["time_out"])
    loop = asyncio.get_event_loop()

    # 使用 Manager 上下文管理器自动清理资源
    with multiprocessing.Manager() as manager:
        queue = manager.Queue()
        process = multiprocessing.Process(target=process_wrapper, args=(fn, queue, *args), kwargs=kwargs)
        process.start()

        # 记录进程PID（使用 task_id 作为 key）
        task_id = kwargs.get("task_id")
        request_id = kwargs.get("request_id")
        logger.debug(f"task_id={task_id}, request_id={request_id}, pid={process.pid}")
        
        if task_id and task_id in job_manager.jobs:
            job_manager.jobs[task_id]["process"] = process
            logger.info(f"Task {task_id} (request_id={request_id}) started with PID: {process.pid}")
        elif request_id and request_id in job_manager.jobs:
            # 兼容旧版本（使用 request_id 作为 key）
            job_manager.jobs[request_id]["process"] = process
            logger.info(f"Task {request_id} started with PID: {process.pid}")

        try:
            # 🔥 改进：使用后台任务监控进程状态，但不影响结果获取
            async def monitor_process():
                """后台监控进程，检测异常退出"""
                check_interval = 0.5  # 每0.5秒检查一次
                while process.is_alive():
                    await asyncio.sleep(check_interval)
                
                # 进程已退出，检查退出代码
                exitcode = process.exitcode
                if exitcode != 0:
                    # 异常退出（段错误、被杀死等）
                    signal_map = {
                        -11: "SIGSEGV (段错误 - 非法内存访问)",
                        -9: "SIGKILL (进程被强制终止)",
                        -15: "SIGTERM (进程被终止)",
                        -6: "SIGABRT (进程异常中止)",
                        -8: "SIGFPE (浮点异常)",
                        -4: "SIGILL (非法指令)",
                    }
                    error_msg = signal_map.get(exitcode, f"进程异常退出 (exitcode={exitcode})")
                    logger.error(f"❌ Process {process.pid} crashed: {error_msg}")
                    
                    # 标记任务为失败
                    if task_id and task_id in job_manager.jobs:
                        job_fail_wrap(job_manager.jobs, task_id, f"进程崩溃: {error_msg}")
                    elif request_id and request_id in job_manager.jobs:
                        job_fail_wrap(job_manager.jobs, request_id, f"进程崩溃: {error_msg}")
            
            # 启动后台监控任务
            monitor_task = asyncio.create_task(monitor_process())
            
            # 主线程：等待队列结果（保持原有的长超时）
            result = await asyncio.wait_for(
                loop.run_in_executor(None, queue.get),
                timeout=timeout  # 完整的超时时间，确保有足够时间传输结果
            )
            
            # 取消监控任务
            monitor_task.cancel()
            try:
                await monitor_task
            except asyncio.CancelledError:
                pass
            
            return result
            
        except asyncio.TimeoutError:
            logger.error(f"Task execution exceeded timeout of {timeout} seconds. Terminating process...")
            # 更新耗时的任务为失败状态
            if "task_id" in kwargs:
                task_id = kwargs["task_id"]
                job_fail_wrap(job_manager.jobs, task_id, "Task execution exceeded timeout of {} seconds.".format(timeout))
            return f"Task execution exceeded timeout of {timeout} seconds."
        finally:
            if process.is_alive():
                process.terminate()  # 先尝试友好终止
            # 设置终止等待超时
            process.join(timeout=5)
            # 如果仍未终止，再强制杀死
            if process.is_alive():
                logger.warning(f"Process {process.pid} not responding, force killing...")
                process.kill()
                # 🔥 修复：添加超时保护，避免永久阻塞事件循环
                process.join(timeout=3)
                
                # 🔥 如果仍然存活，放弃等待（避免死锁）
                if process.is_alive():
                    logger.error(
                        f"❌ Process {process.pid} stuck in unkillable state (D-state), "
                        f"abandoning cleanup to prevent event loop deadlock. "
                        f"This process will become a zombie and be reaped by periodic cleanup."
                    )
            
            logger.debug(f"Process {process.pid} cleanup completed, exitcode={process.exitcode}")
            
            # 🔥 主动回收僵尸进程
            cleanup_zombie_processes()


async def start_deep_parse_task(task_id: str, **kwargs):
    """
    启动深度解析任务
    """
    try:
        logger.info(f"Task {task_id} started with args: {kwargs}")
        
        # 🔥 将 task_id 添加到 kwargs 中，确保传递给 parse_file_deepdoc
        kwargs['task_id'] = task_id
        
        # 在进程池中运行
        result = await run_in_process(loader_api.parse_file_deepdoc, **kwargs)
        
        # 🔥 检查任务是否已被后台监控器标记为失败（进程崩溃）
        if task_id in job_manager.jobs and job_manager.jobs[task_id]["status"] == "failed":
            logger.info(f"Task {task_id} already marked as failed by monitor (process crashed), preserving error info")
            return  # 保留详细的崩溃错误信息
        
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
            # 对于预签名 URL，移除查询参数后检查
            url_path = oss_url.split('?')[0]  # 移除查询参数
            if url_path.endswith("json") or url_path.endswith(".json"):
                job_manager.jobs[task_id]["result"] = oss_url  # 解析结果 URL
                job_manager.jobs[task_id]["pdf_file"] = pdf_file  # PDF 文件下载链接
                job_manager.jobs[task_id]["status"] = "complete"
                job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)  # 记录完成时间
                logger.debug("job_manager success={}".format(job_manager.jobs))
                logger.info(f"Task {task_id}, {job_manager.jobs[task_id]['filename']} completed successfully. pdf_file={pdf_file}")
            else:
                logger.warning(f"Task {task_id} result format incorrect: {url_path}")
                job_fail_wrap(job_manager.jobs, task_id, f"解析结果格式错误: {url_path}")
        else:
            logger.error(f"Task {task_id} result is None or invalid: {result}")
            job_fail_wrap(job_manager.jobs, task_id, f"解析失败，结果无效: {result}")
    except Exception as ex:
        job_fail_wrap(job_manager.jobs, task_id, ex)