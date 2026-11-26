#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""双拼方案：小鹤双拼 / 微软双拼 / 自然码。

decode_shuangpin(keys, scheme) -> 若干种解码路径 [[(音节, 是否前缀), ...]]
每 2 键 1 音节；末尾单键视为不完整音节（前缀）。
键位映射来源：flypy.cc / shuangpin.xyz / laidazi.com（见 docs/engine-spec.md）。
"""
from __future__ import annotations

from itertools import product

from .pinyin import SYLLABLES

# 特殊声母（三方案一致）
SPECIAL_INITIALS: dict[str, str] = {"v": "zh", "i": "ch", "u": "sh"}

# ---------------------------------------------------------------- 小鹤双拼
_XIAOHE_FINALS: dict[str, list[str]] = {
    "a": ["a"], "b": ["in"], "c": ["ao"], "d": ["ai"], "e": ["e"],
    "f": ["en"], "g": ["eng"], "h": ["ang"], "i": ["i"], "j": ["an"],
    "k": ["ing", "uai"], "l": ["iang", "uang"], "m": ["ian"], "n": ["iao"],
    "o": ["o", "uo"], "p": ["ie"], "q": ["iu"], "r": ["uan", "van"],
    "s": ["ong", "iong"], "t": ["ue", "ve"], "u": ["u"], "v": ["ui", "v"],
    "w": ["ei"], "x": ["ia", "ua"], "y": ["un", "vn"], "z": ["ou"],
}

# ---------------------------------------------------------------- 微软双拼
_MSPY_FINALS: dict[str, list[str]] = {
    "a": ["a"], "b": ["ou"], "c": ["iao"], "d": ["iang", "uang"], "e": ["e"],
    "f": ["en"], "g": ["eng"], "h": ["ang"], "i": ["i"], "j": ["an"],
    "k": ["ao"], "l": ["ai"], "m": ["ian"], "n": ["in"],
    "o": ["o", "uo"], "p": ["un", "vn"], "q": ["iu"],
    "r": ["uan", "van", "er"], "s": ["ong", "iong"], "t": ["ue", "ve"],
    "u": ["u"], "v": ["ui"], "w": ["ia", "ua"], "x": ["ie"],
    "y": ["uai", "v"], "z": ["ei"], ";": ["ing"],
}

# ---------------------------------------------------------------- 自然码
_ZIRANMA_FINALS: dict[str, list[str]] = {
    "a": ["a"], "b": ["ou"], "c": ["iao"], "d": ["iang", "uang"], "e": ["e"],
    "f": ["en"], "g": ["eng"], "h": ["ang"], "i": ["i"], "j": ["an"],
    "k": ["ao"], "l": ["ai"], "m": ["ian"], "n": ["in"],
    "o": ["o", "uo"], "p": ["un", "vn"], "q": ["iu"],
    "r": ["uan", "van", "er"], "s": ["ong", "iong"], "t": ["ue", "ve"],
    "u": ["u"], "v": ["ui", "v"], "w": ["ia", "ua"], "x": ["ie"],
    "y": ["ing", "uai"], "z": ["ei"],
}

SCHEMES: dict[str, dict] = {
    "xiaohe": {"name": "小鹤双拼", "finals": _XIAOHE_FINALS, "zero": "literal"},
    "mspy": {"name": "微软双拼", "finals": _MSPY_FINALS, "zero": "o"},
    "ziranma": {"name": "自然码", "finals": _ZIRANMA_FINALS, "zero": "literal"},
}


def _fin_variants(fin: str, ini: str) -> list[str]:
    """j/q/x/y 后的 ü 按 u 书写。"""
    out = [fin]
    alt = fin.replace("v", "u")
    if alt != fin:
        out.append(alt)
    return out


def _group_options(k1: str, k2: str | None, scheme: dict) -> list[tuple[str, bool]]:
    """一个 2 键组（或末尾 1 键）的所有可能解释。"""
    finals: dict[str, list[str]] = scheme["finals"]
    opts: list[tuple[str, bool]] = []

    if k2 is None:
        # 末尾单键：声母/韵母前缀
        ini = SPECIAL_INITIALS.get(k1, k1)
        opts.append((ini, True))
        return opts

    # ---- 1) 常规：声母 + 韵母
    ini = SPECIAL_INITIALS.get(k1, k1)
    for fin0 in finals.get(k2, [k2]):
        for fin in _fin_variants(fin0, ini):
            syl = ini + fin
            if syl in SYLLABLES and (syl, False) not in opts:
                opts.append((syl, False))

    # ---- 2) 零声母
    if scheme["zero"] == "o":
        # 微软：O + 韵母键
        if k1 == "o":
            for fin0 in finals.get(k2, []):
                if fin0 in SYLLABLES and (fin0, False) not in opts:
                    opts.append((fin0, False))
                # oo = o/uo 都保留
                if fin0 in ("o", "uo") and k2 == "o":
                    if (fin0, False) not in opts:
                        opts.append((fin0, False))
    else:
        # 小鹤 / 自然码
        if k1 in ("a", "o", "e"):
            if k1 == k2 and (k1, False) not in opts:
                opts.append((k1, False))
            lit = k1 + k2
            if lit in SYLLABLES and (lit, False) not in opts:
                opts.append((lit, False))
            for fin0 in finals.get(k2, []):
                # 三字母韵母：首字母 + 韵母所在键（ang -> AH）
                if fin0.startswith(k1) and fin0 in SYLLABLES and (fin0, False) not in opts:
                    opts.append((fin0, False))

    if not opts:
        # 兜底：拼不出时当作前缀
        opts.append((ini + (finals.get(k2, [k2])[0] if k2 else ""), True))
    return opts


def decode_shuangpin(keys: str, scheme_name: str) -> list[list[tuple[str, bool]]]:
    """把双拼按键序列解码为若干候选路径。"""
    scheme = SCHEMES.get(scheme_name)
    if not scheme:
        raise ValueError(f"未知双拼方案: {scheme_name}")
    keys = keys.replace("'", "")

    groups: list[list[tuple[str, bool]]] = []
    i = 0
    while i < len(keys):
        k1 = keys[i]
        k2 = keys[i + 1] if i + 1 < len(keys) else None
        groups.append(_group_options(k1, k2, scheme))
        i += 2 if k2 is not None else 1

    if not groups:
        return [[]]
    paths: list[list[tuple[str, bool]]] = []
    for combo in product(*groups):
        paths.append(list(combo))
        if len(paths) >= 8:
            break
    return paths
