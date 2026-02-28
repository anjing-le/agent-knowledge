from kparser.model.vlm_service import API_GLM
from kparser.common.log_utils import get_logger
from kparser.common.config import VISION

logger = get_logger(__name__)


# multimodal for vision understanding
def get_vision_model():
    logger.info("get_vision_model")
    # 使用VISION配置中的值
    model_channel = "API_GLM"
    model_code = VISION.get("vlm_model", "glm-4v-plus-0111")
    logger.info("get_vision_model model_channel={}, model_code={}".format(model_channel, model_code))
    if model_channel == "API_GLM":
        logger.info("creating API_GLM")
        return API_GLM()
    else:
        raise Exception(f"model_channel: {model_channel} not supported")