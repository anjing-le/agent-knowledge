import os
import json
import importlib
import re
from cachetools import LRUCache, cached
from ruamel.yaml import YAML
from . import log_utils

logger = log_utils.get_logger(__name__)


SERVICE_CONF = "service_conf.yaml"
PROJECT_BASE = os.getenv("RAG_PROJECT_BASE") or os.getenv("RAG_DEPLOY_BASE")


def conf_realpath(conf_name):
    conf_path = f"config/{conf_name}"
    return os.path.join(get_project_base_directory(), conf_path)


def resolve_env_vars(value):
    """解析环境变量，支持${VAR:-default}格式"""
    if isinstance(value, str):
        # 匹配${VAR:-default}格式
        pattern = r'\$\{([^:}]+)(?::-([^}]*))?\}'
        
        def replace_env_var(match):
            var_name = match.group(1)
            default_value = match.group(2) if match.group(2) is not None else ''
            
            # 从环境变量获取值
            env_value = os.environ.get(var_name)
            if env_value is not None:
                return env_value
            else:
                return default_value
        
        return re.sub(pattern, replace_env_var, value)
    return value


def resolve_env_vars_recursive(data):
    """递归解析数据结构中的环境变量"""
    if isinstance(data, dict):
        return {key: resolve_env_vars_recursive(value) for key, value in data.items()}
    elif isinstance(data, list):
        return [resolve_env_vars_recursive(item) for item in data]
    else:
        return resolve_env_vars(data)


def read_config(conf_name=SERVICE_CONF):
    local_config = {}
    local_path = conf_realpath(f'local.{conf_name}')

    # load local config file
    if os.path.exists(local_path):
        local_config = load_yaml_conf(local_path)
        if not isinstance(local_config, dict):
            raise ValueError(f'Invalid config file: "{local_path}".')

    global_config_path = conf_realpath(conf_name)
    global_config = load_yaml_conf(global_config_path)

    if not isinstance(global_config, dict):
        raise ValueError(f'Invalid config file: "{global_config_path}".')

    global_config.update(local_config)
    return global_config


def show_configs():
    msg = f"Current configs, from {conf_realpath(SERVICE_CONF)}:"
    for k, v in CONFIGS.items():
        msg += f"\n\t{k}: {v}"
    logger.info(msg)


def get_base_config(key, default=None):
    if key is None:
        return None
    if default is None:
        default = os.environ.get(key.upper())
    return CONFIGS.get(key, default)


def decrypt_database_config(
        database=None, passwd_key="password", name="database"):
    if not database:
        database = get_base_config(name, {})

    database[passwd_key] = decrypt_database_password(database[passwd_key])
    return database


def decrypt_database_password(password):
    encrypt_password = get_base_config("encrypt_password", False)
    encrypt_module = get_base_config("encrypt_module", False)
    private_key = get_base_config("private_key", None)

    if not password or not encrypt_password:
        return password

    if not private_key:
        raise ValueError("No private key")

    module_fun = encrypt_module.split("#")
    pwdecrypt_fun = getattr(
        importlib.import_module(
            module_fun[0]),
        module_fun[1])

    return pwdecrypt_fun(private_key, password)


def get_project_base_directory(*args):
    global PROJECT_BASE
    if PROJECT_BASE is None:
        PROJECT_BASE = os.path.abspath(
            os.path.join(
                os.path.dirname(os.path.realpath(__file__)),
                os.pardir,
            )
        )

    if args:
        return os.path.join(PROJECT_BASE, *args)
    return PROJECT_BASE


@cached(cache=LRUCache(maxsize=10))
def load_json_conf(conf_path):
    if os.path.isabs(conf_path):
        json_conf_path = conf_path
    else:
        json_conf_path = os.path.join(get_project_base_directory(), conf_path)
    try:
        with open(json_conf_path) as f:
            return json.load(f)
    except BaseException:
        raise EnvironmentError(
            "loading json file config from '{}' failed!".format(json_conf_path)
        )


def dump_json_conf(config_data, conf_path):
    if os.path.isabs(conf_path):
        json_conf_path = conf_path
    else:
        json_conf_path = os.path.join(get_project_base_directory(), conf_path)
    try:
        with open(json_conf_path, "w") as f:
            json.dump(config_data, f, indent=4)
    except BaseException:
        raise EnvironmentError(
            "loading json file config from '{}' failed!".format(json_conf_path)
        )


def load_json_conf_real_time(conf_path):
    if os.path.isabs(conf_path):
        json_conf_path = conf_path
    else:
        json_conf_path = os.path.join(get_project_base_directory(), conf_path)
    try:
        with open(json_conf_path) as f:
            return json.load(f)
    except BaseException:
        raise EnvironmentError(
            "loading json file config from '{}' failed!".format(json_conf_path)
        )


def load_yaml_conf(conf_path):
    if not os.path.isabs(conf_path):
        conf_path = os.path.join(get_project_base_directory(), conf_path)
    try:
        with open(conf_path) as f:
            yaml = YAML(typ='safe', pure=True)
            config = yaml.load(f)
            
            # 解析环境变量
            if config is not None:
                config = resolve_env_vars_recursive(config)
            
            return config
    except Exception as e:
        raise EnvironmentError(
            "loading yaml file config from {} failed:".format(conf_path), e
        )


def rewrite_yaml_conf(conf_path, config):
    if not os.path.isabs(conf_path):
        conf_path = os.path.join(get_project_base_directory(), conf_path)
    try:
        with open(conf_path, "w") as f:
            yaml = YAML(typ="safe")
            yaml.dump(config, f)
    except Exception as e:
        raise EnvironmentError(
            "rewrite yaml file config {} failed:".format(conf_path), e
        )


CONFIGS = read_config()
# logger.info("CONFIGS={}".format(CONFIGS))


# Server
RAG_CONF_PATH = os.path.join(get_project_base_directory(), "config")
S3 = get_base_config("s3", {})
TOS = get_base_config("tos", {})
MINIO = decrypt_database_config(name="minio")
SERVICE = get_base_config("service", {})
VISION = get_base_config("vision", {})
PARALLEL_DEVICES = get_base_config("parallel_devices", 1)
DELAY_LEVELS = get_base_config("delay_levels", {})
RETRY_DELAY = get_base_config("retry_delay", {})
REDIS = get_base_config("redis", {})
TIMEOUT = get_base_config("timeout", {})
TOPIC = get_base_config("topic", {})
RABBITMQ = get_base_config("rabbitmq", {})