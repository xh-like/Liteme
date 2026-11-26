"""LiteIME 核心引擎（Python 实现）。

Windows 输入工具直接引用本包；Android 端在
android/app/src/main/java/com/liteime/core/ 下有算法一致的 Kotlin 移植，
两端共享 data/ 目录下的词库与笔画数据。
"""
from .dictionary import Dictionary
from .engine import PinyinEngine, QueryResult
from .handwriting import HandwritingRecognizer, classify_stroke
from .predictor import Predictor
from .shuangpin import SCHEMES, decode_shuangpin

__all__ = [
    "Dictionary",
    "PinyinEngine",
    "QueryResult",
    "HandwritingRecognizer",
    "classify_stroke",
    "Predictor",
    "SCHEMES",
    "decode_shuangpin",
]
