#!/usr/bin/env python3
"""
批量发送任务到 RabbitMQ 队列（用于压测）
"""
import sys
import time
from send_task_message import send_task_to_queue

def batch_send_tasks(
    document_parse_count=50,
    ocr_recognition_count=50,
    original_url='knowledge-center-dev/shark/1-1 PKG 普.pdf',
    doc_type='DOCUMENT_BASIC',
    interval=0.1
):
    """
    批量发送任务
    
    Args:
        document_parse_count: documentParse 任务数量
        ocr_recognition_count: ocrRecognition 任务数量
        original_url: 文件存储地址
        doc_type: 文档类型（用于选择解析参数配置）
        interval: 发送间隔（秒）
    
    Returns:
        成功发送的任务ID列表
    """
    print("=" * 80)
    print("🚀 批量发送任务 (压测模式)")
    print("=" * 80)
    print()
    print(f"📊 发送计划:")
    print(f"   documentParse 任务: {document_parse_count} 个")
    print(f"   ocrRecognition 任务: {ocr_recognition_count} 个")
    print(f"   总计: {document_parse_count + ocr_recognition_count} 个任务")
    print(f"   文件: {original_url}")
    print(f"   docType: {doc_type} (用于加载解析配置)")
    print(f"   发送间隔: {interval} 秒")
    print()
    
    input("按 Enter 开始发送...")
    print()
    
    task_ids = []
    success_count = 0
    failed_count = 0
    start_time = time.time()
    
    # 发送 documentParse 任务
    print("=" * 80)
    print(f"📤 发送 documentParse 任务 ({document_parse_count} 个)")
    print("=" * 80)
    print()
    
    for i in range(document_parse_count):
        task_id = send_task_to_queue(
            task_action='documentParse',
            original_url=original_url,
            doc_type=doc_type,
            task_name=f"压测任务-documentParse-{i+1}"
        )
        
        if task_id:
            task_ids.append(task_id)
            success_count += 1
        else:
            failed_count += 1
        
        if interval > 0:
            time.sleep(interval)
    
    # 发送 ocrRecognition 任务
    print()
    print("=" * 80)
    print(f"📤 发送 ocrRecognition 任务 ({ocr_recognition_count} 个)")
    print("=" * 80)
    print()
    
    for i in range(ocr_recognition_count):
        task_id = send_task_to_queue(
            task_action='ocrRecognition',
            original_url=original_url,
            doc_type=doc_type,
            task_name=f"压测任务-ocrRecognition-{i+1}"
        )
        
        if task_id:
            task_ids.append(task_id)
            success_count += 1
        else:
            failed_count += 1
        
        if interval > 0:
            time.sleep(interval)
    
    elapsed_time = time.time() - start_time
    
    # 显示统计
    print()
    print("=" * 80)
    print("📊 发送统计")
    print("=" * 80)
    print(f"   成功: {success_count} 个任务")
    print(f"   失败: {failed_count} 个任务")
    print(f"   耗时: {elapsed_time:.2f} 秒")
    print(f"   平均速率: {success_count / elapsed_time:.2f} 任务/秒")
    print()
    
    if task_ids:
        print("✅ 所有任务已发送到队列！")
        print()
        print("💡 提示:")
        print("   使用以下命令消费结果:")
        print("   python consume_result_queue.py --auto-stop")
        print()
        print("   或持续监听:")
        print("   python consume_result_queue.py")
    else:
        print("❌ 没有任务发送成功")
    
    print("=" * 80)
    
    return task_ids


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='批量发送任务到 RabbitMQ 队列（压测）')
    parser.add_argument(
        '--document-parse',
        type=int,
        default=50,
        help='documentParse 任务数量 (默认: 50)'
    )
    parser.add_argument(
        '--ocr-recognition',
        type=int,
        default=50,
        help='ocrRecognition 任务数量 (默认: 50)'
    )
    parser.add_argument(
        '--url',
        default='knowledge-center-dev/shark/1-1 PKG 普.pdf',
        help='文件存储地址'
    )
    parser.add_argument(
        '--doc-type',
        default='DOCUMENT_BASIC',
        help='文档类型 (默认: DOCUMENT_BASIC)'
    )
    parser.add_argument(
        '--interval',
        type=float,
        default=0.1,
        help='发送间隔（秒，默认: 0.1）'
    )
    parser.add_argument(
        '--quick',
        action='store_true',
        help='快速测试模式 (每种类型 5 个任务)'
    )
    
    args = parser.parse_args()
    
    # 快速测试模式
    if args.quick:
        args.document_parse = 5
        args.ocr_recognition = 5
        print("🚀 快速测试模式: 每种类型 5 个任务")
        print()
    
    task_ids = batch_send_tasks(
        document_parse_count=args.document_parse,
        ocr_recognition_count=args.ocr_recognition,
        original_url=args.url,
        doc_type=args.doc_type,
        interval=args.interval
    )
    
    return 0 if task_ids else 1


if __name__ == '__main__':
    sys.exit(main())

