import subprocess

def cuda_is_available():
    try:
        output = subprocess.check_output("nvidia-smi", stderr=subprocess.DEVNULL)
        return b"CUDA Version" in output
    except Exception:
        return False