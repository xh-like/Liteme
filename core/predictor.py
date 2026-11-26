#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""联想预测：根据已上屏文本推荐后续输入。"""
from __future__ import annotations

from .dictionary import Dictionary


class Predictor:
    def __init__(self, dictionary: Dictionary) -> None:
        self.dict = dictionary

    def predict(self, committed: str, n: int = 8) -> list[str]:
        """committed 为已上屏的最近文本；返回联想候选。"""
        if not committed:
            return []
        out: list[str] = []
        seen: set[str] = set()

        def push(w: str) -> None:
            if w and w not in seen and w != committed:
                seen.add(w)
                out.append(w)

        # 1) 以最近 1-2 字开头的词（补全联想）
        for k in (2, 1):
            prefix = committed[-k:]
            hits = [
                e for e in self.dict.words
                if e.word.startswith(prefix) and len(e.word) > len(prefix)
            ]
            hits.sort(key=lambda e: -e.freq)
            for e in hits[: n * 2]:
                push(e.word)

        # 2) 单字联想：以最后一个字开头的常用词
        last = committed[-1]
        hits = [
            e for e in self.dict.words
            if e.word.startswith(last) and len(e.word) > 1
        ]
        hits.sort(key=lambda e: -e.freq)
        for e in hits[: n * 2]:
            push(e.word)

        return out[:n]
