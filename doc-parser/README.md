# Knowledge Center Parser

知识中心文档解析服务，支持多种文档格式的解析和多模态内容理解。

## 功能特性

- 支持多种文档格式：PDF、DOCX、XLSX、XLS、PPTX、PNG、JPEG、TXT、MARKDOWN、HTML、JSON、JSONL等
- 支持版面分析、OCR识别、跨页表格识别等能力；
- 多模态内容理解：图像识别、表格解析
- 统一配置管理：通过环境变量和配置文件管理所有配置
- 多种存储后端：支持TOS、MinIO、S3等对象存储

## 配置管理

### 统一配置架构

项目采用统一的配置管理方式，所有配置都通过以下层次管理：

1. **环境变量**：最高优先级，可通过`.env`文件或系统环境变量设置
2. **配置文件**：`kparser/config/service_conf.yaml`，包含默认配置
3. **代码默认值**：最低优先级，作为最终回退

### 主要配置项

#### Vision配置

```yaml
# kparser/config/service_conf.yaml
vision:
  vlm_api_key: ${VLM_API_KEY:-your-vlm-api-key-here}
  vlm_api_base: ${VLM_API_BASE:-https://open.bigmodel.cn/api/paas/v4}
  vlm_model: ${VLM_MODEL:-glm-4v-plus-0111}
  excel_vision_prompt: ${EXCEL_VISION_PROMPT:-请仔细分析这张图片中的所有内容...}
  image_prompt: ${IMAGE_PROMPT:-请根据图片内容生成一段对图片的描述}
  table_prompt: ${TABLE_PROMPT:-你是一个多模态智能助手，具备理解图像和自然语言的能力...}
```

#### TOS存储配置

```yaml
tos:
  ak: ${TOS_AK:-your-tos-access-key}
  sk: ${TOS_SK:-your-tos-secret-key}
  region: ${TOS_REGION:-cn-beijing}
  endpoint: ${TOS_ENDPOINT:-tos-cn-beijing.volces.com}
  bucket: ${TOS_BUCKET:-knowledge-center-dev}
  temp_object_key_prefix: ${TOS_TEMP_OBJECT_KEY_PREFIX:-shark}
```

### 环境变量设置

创建`.env`文件（参考`.env.example`）：

```bash
# Vision配置
VLM_API_KEY=your_vlm_api_key
VLM_API_BASE=https://open.bigmodel.cn/api/paas/v4
VLM_MODEL=glm-4v-plus-0111
EXCEL_VISION_PROMPT=请仔细分析这张图片中的所有内容...
IMAGE_PROMPT=请根据图片内容生成一段对图片的描述
TABLE_PROMPT=你是一个多模态智能助手，具备理解图像和自然语言的能力...

# TOS配置
TOS_AK=your_tos_ak
TOS_SK=your_tos_sk
TOS_REGION=cn-beijing
TOS_ENDPOINT=tos-cn-beijing.volces.com
TOS_BUCKET=your_bucket
TOS_TEMP_OBJECT_KEY_PREFIX=shark
```

### 配置使用

#### 在代码中使用配置

```python
from kparser.common.config import VISION, TOS

# 使用Vision配置
api_key = VISION.get("vlm_api_key")
api_base = VISION.get("vlm_api_base")
model = VISION.get("vlm_model")
image_prompt = VISION.get("image_prompt")
table_prompt = VISION.get("table_prompt")

# 使用TOS配置
tos_ak = TOS.get("ak")
tos_bucket = TOS.get("bucket")
```

#### Vision Model配置

`model_api.py`和`vlm_service.py`现在完全使用VISION配置中的默认值：

- 系统不再接受`vision_config`参数
- 所有配置都从`VISION`配置块中读取
- 支持通过环境变量覆盖默认配置
- 确保配置的一致性和简化性

## 安装和运行

### 依赖安装

```bash
pip install -r requirements.txt
```

### 环境配置

1. 复制环境变量模板：
```bash
cp .env.example .env
```

2. 编辑`.env`文件，设置你的配置值

### 运行服务

```bash
python -m kparser.app
```

## 测试

### 配置测试

```bash
# 测试VISION配置读取
python test_vision_config.py

# 测试Vision Model配置
python test_vision_model_config.py

# 测试完整环境变量配置
python test_env_config_full.py
```

### API测试

```bash
# 测试Excel Vision解析
python test_excel_vision_parser.py
```

## 架构说明

### 配置层次

```
环境变量 (.env) → 配置文件 (service_conf.yaml) → 代码默认值
     ↑                    ↑                        ↑
  最高优先级           中等优先级               最低优先级
```

### 模块依赖

- `kparser.common.config`: 统一配置管理
- `kparser.model.model_api`: Vision模型API
- `kparser.model.vlm_service`: Vision模型服务
- `kparser.parserground.parser.excel_vision_parser`: Excel Vision解析器

### 配置更新历史

- **v1.0**: 硬编码配置
- **v2.0**: 环境变量支持
- **v3.0**: 统一配置管理
  - 将`excel_vision`重命名为`vision`
  - 统一`model_api.py`和`vlm_service.py`的配置来源
  - 支持配置覆盖和默认值回退
  - 将`prompts`配置合并到`vision`中
  - 统一参数命名：`openai_*` → `vlm_*`，`vision_prompt` → `image_prompt`
- **v4.0**: 简化配置接口（当前版本）
  - 移除`vision_config`参数
  - 系统完全使用`VISION`配置中的默认值
  - 简化API接口，提高配置一致性

## 注意事项

1. 确保`.env`文件不被提交到版本控制系统
2. 生产环境建议使用系统环境变量而不是`.env`文件
3. 配置更改后需要重启服务才能生效
4. 所有配置项都有默认值，确保服务的稳定性
5. 新的配置参数名称：
   - `openai_api_key` → `vlm_api_key`
   - `openai_api_base` → `vlm_api_base`
   - `openai_model` → `vlm_model`
   - `vision_prompt` → `image_prompt`
6. **重要变更**：`vision_config`参数已被移除，系统现在完全使用`VISION`配置中的默认值