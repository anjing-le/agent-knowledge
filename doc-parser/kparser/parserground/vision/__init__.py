import io
import pdfplumber

from .ocr import OCR
from .recognizer import Recognizer
from .layout_recognizer import LayoutRecognizer4YOLOv10 as LayoutRecognizer
from .table_structure_recognizer import TableStructureRecognizer


def init_in_out(args):
    from PIL import Image
    import os
    import traceback
    from kparser.common.file_utils import traversal_files
    images = []
    outputs = []

    if not os.path.exists(args.output_dir):
        os.mkdir(args.output_dir)

    def pdf_pages(fnm, zoomin=3):
        from pdf2image import convert_from_path
        nonlocal outputs, images
        # 🔥 使用pdf2image转换PDF页面，更稳定
        dpi = 72 * zoomin
        images = convert_from_path(fnm, dpi=dpi, fmt='png')
        
        for i, page in enumerate(images):
            outputs.append(os.path.split(fnm)[-1] + f"_{i}.jpg")

    def images_and_outputs(fnm):
        nonlocal outputs, images
        if fnm.split(".")[-1].lower() == "pdf":
            pdf_pages(fnm)
            return
        try:
            # images.append(Image.open(fnm))
            # outputs.append(os.path.split(fnm)[-1])
            fp = open(fnm, 'rb')
            binary = fp.read()
            fp.close()
            images.append(Image.open(io.BytesIO(binary)).convert('RGB'))
            outputs.append(os.path.split(fnm)[-1])
        except Exception:
            traceback.print_exc()

    if os.path.isdir(args.inputs):
        for fnm in traversal_files(args.inputs):
            images_and_outputs(fnm)
    else:
        images_and_outputs(args.inputs)

    for i in range(len(outputs)):
        outputs[i] = os.path.join(args.output_dir, outputs[i])

    return images, outputs


__all__ = [
    "OCR",
    "Recognizer",
    "LayoutRecognizer",
    "TableStructureRecognizer",
    "init_in_out",
]
