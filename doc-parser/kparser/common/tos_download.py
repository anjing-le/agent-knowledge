import os
import tos

ak = os.getenv('TOS_AK', 'your-tos-access-key')
sk = os.getenv('TOS_SK', 'your-tos-secret-key')
# your endpoint 和 your region 填写Bucket 所在区域对应的Endpoint。# 以华北2(北京)为例，your endpoint 填写 tos-cn-beijing.volces.com，your region 填写 cn-beijing。
endpoint = 'tos-cn-beijing.volces.com'
region = 'cn-beijing'
bucket_name = 'knowledge-center-dev'
object_key = "shark/test_upload.pptx"

# try:
#     # 创建 TosClientV2 对象，对桶和对象的操作都通过 TosClientV2 实现
#     client = tos.TosClientV2(ak, sk, endpoint, region)
#     object_stream = client.get_object(bucket_name, object_key)
#     # get_object返回的是一个可迭代对象，迭代读取对象内容
#     # for content in object_stream:
#     #     print(content)
#     # 也可直接调用read()方法在内存中获取完整的数据
#     print(object_stream.read())
# except tos.exceptions.TosClientError as e:
#     # 操作失败，捕获客户端异常，一般情况为非法请求参数或网络异常
#     print('fail with client error, message:{}, cause: {}'.format(e.message, e.cause))
# except tos.exceptions.TosServerError as e:
#     # 操作失败，捕获服务端异常，可从返回信息中获取详细错误信息
#     print('fail with server error, code: {}'.format(e.code))
#     # request id 可定位具体问题，强烈建议日志中保存
#     print('error with request id: {}'.format(e.request_id))
#     print('error with message: {}'.format(e.message))
#     print('error with http code: {}'.format(e.status_code))
#     print('error with ec: {}'.format(e.ec))
#     print('error with request url: {}'.format(e.request_url))
# except Exception as e:
#     print('fail with unknown error: {}'.format(e))

file_name = "/home/dev/youzhiqiang/knowledge_center_parser/data/ch5_download.pptx"
try:
    # 创建 TosClientV2 对象，对桶和对象的操作都通过 TosClientV2 实现
    client = tos.TosClientV2(ak, sk, endpoint, region)
    # 若 file_name 为目录则将对象下载到此目录下, 文件名为对象名
    client.get_object_to_file(bucket_name, object_key, file_name)
except tos.exceptions.TosClientError as e:
    # 操作失败，捕获客户端异常，一般情况为非法请求参数或网络异常
    print('fail with client error, message:{}, cause: {}'.format(e.message, e.cause))
except tos.exceptions.TosServerError as e:
    # 操作失败，捕获服务端异常，可从返回信息中获取详细错误信息
    print('fail with server error, code: {}'.format(e.code))
    # request id 可定位具体问题，强烈建议日志中保存
    print('error with request id: {}'.format(e.request_id))
    print('error with message: {}'.format(e.message))
    print('error with http code: {}'.format(e.status_code))
    print('error with ec: {}'.format(e.ec))
    print('error with request url: {}'.format(e.request_url))
except Exception as e:
    print('fail with unknown error: {}'.format(e))