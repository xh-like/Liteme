#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""词典加载与检索。

数据文件格式见 tools/build_data.py：
  chars_pinyin.txt  char<TAB>py1,py2<TAB>freq
  words.txt         word<TAB>syl1 syl2<TAB>freq
  strokes.txt       char<TAB>hspnz 序列
"""
from __future__ import annotations

import os
from bisect import bisect_left
from dataclasses import dataclass, field

from .pinyin import syl_eq, syl_has_prefix


@dataclass(slots=True)
class WordEntry:
    word: str
    syls: tuple[str, ...]
    freq: int


@dataclass
class Dictionary:
    chars: dict[str, list[str]] = field(default_factory=dict)   # 字 -> [读音]
    char_freq: dict[str, int] = field(default_factory=dict)     # 字 -> 词频
    words: list[WordEntry] = field(default_factory=list)
    strokes: dict[str, str] = field(default_factory=dict)       # 字 -> 笔画序列
    _by_first: dict[str, list[WordEntry]] = field(default_factory=dict, repr=False)
    _first_sorted: list[str] = field(default_factory=list, repr=False)

    # ------------------------------------------------------------ 加载
    @classmethod
    def load(cls, data_dir: str) -> "Dictionary":
        d = cls()
        d._load_chars(os.path.join(data_dir, "chars_pinyin.txt"))
        d._load_words(os.path.join(data_dir, "words.txt"))
        d._load_strokes(os.path.join(data_dir, "strokes.txt"))
        d._build_index()
        return d

    def _load_chars(self, path: str) -> None:
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip():
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 2:
                    continue
                char = parts[0]
                readings = [p for p in parts[1].split(",") if p]
                freq = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
                self.chars[char] = readings
                self.char_freq[char] = freq

    def _load_words(self, path: str) -> None:
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip():
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 2:
                    continue
                word = parts[0]
                syls = tuple(parts[1].split())
                if len(syls) != len(word):
                    continue
                freq = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 1
                self.words.append(WordEntry(word, syls, freq))

    def _load_strokes(self, path: str) -> None:
        if not os.path.exists(path):
            return
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip():
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 2:
                    self.strokes[parts[0]] = parts[1]

    def _build_index(self) -> None:
        self._by_first.clear()
        for e in self.words:
            self._by_first.setdefault(e.syls[0], []).append(e)
        for lst in self._by_first.values():
            lst.sort(key=lambda e: -e.freq)
        self._first_sorted = sorted(self._by_first.keys())
        # 单字读音倒排索引（读音 -> [(字, 词频)] 按词频降序）
        self._chars_by_syl: dict[str, list[tuple[str, int]]] = {}
        for ch, readings in self.chars.items():
            fr = self.char_freq.get(ch, 0)
            for r in readings:
                self._chars_by_syl.setdefault(r, []).append((ch, fr))
        for lst in self._chars_by_syl.values():
            lst.sort(key=lambda t: -t[1])
        self._chars_syl_sorted = sorted(self._chars_by_syl.keys())

    # ------------------------------------------------------------ 检索
    @staticmethod
    def _prefix_range(keys: list[str], prefix: str) -> tuple[int, int]:
        """返回有序 keys 中以 prefix 开头的区间 [lo, hi)。"""
        lo = bisect_left(keys, prefix)
        hi = lo
        n = len(keys)
        while hi < n and keys[hi].startswith(prefix):
            hi += 1
        return lo, hi

    def _first_bucket(self, first: str, prefix: bool, fuzzy: bool = False):
        if fuzzy:
            from .pinyin import fold_fuzzy
            ff = fold_fuzzy(first)
            out: list[WordEntry] = []
            for key, lst in self._by_first.items():
                fk = fold_fuzzy(key)
                if fk == ff or (prefix and fk.startswith(ff)):
                    out.extend(lst)
            return out
        if not prefix:
            return self._by_first.get(first, ())
        lo, hi = self._prefix_range(self._first_sorted, first)
        out = []
        for s in self._first_sorted[lo:hi]:
            out.extend(self._by_first[s])
        return out

    def find_words(
        self,
        syls: list[str],
        last_prefix: bool = False,
        all_prefix: bool = False,
        fuzzy: bool = False,
        allow_longer: bool = True,
        limit: int = 200,
    ) -> list[tuple[WordEntry, int]]:
        """查找与音节序列匹配的词，返回 (entry, match_rank)。

        match_rank: 0=整词长度完全匹配 1=输入是词的前缀(补全) 2=简拼(每个音节取前缀)
        """
        if not syls:
            return []
        n = len(syls)
        results: list[tuple[WordEntry, int]] = []
        bucket = self._first_bucket(
            syls[0],
            prefix=all_prefix or len(syls[0]) < 2,
            fuzzy=fuzzy,
        )
        for e in bucket:
            m = len(e.syls)
            if m < n or (m > n and not allow_longer):
                continue
            ok = True
            for i in range(min(n, m)):
                q = syls[i]
                t = e.syls[i]
                is_last = i == n - 1
                if all_prefix:
                    if not syl_has_prefix(t, q, fuzzy):
                        ok = False
                        break
                elif is_last and last_prefix:
                    if not (syl_has_prefix(t, q, fuzzy) or syl_eq(q, t, fuzzy)):
                        ok = False
                        break
                else:
                    if not syl_eq(q, t, fuzzy):
                        ok = False
                        break
            if not ok:
                continue
            if m == n:
                rank = 2 if all_prefix and any(
                    syls[i] != e.syls[i] for i in range(n)
                ) else 0
            else:
                rank = 1
            results.append((e, rank))
        results.sort(key=lambda t: (t[1], -t[0].freq))
        return results[:limit]

    def find_chars(
        self,
        syl: str,
        last_prefix: bool = False,
        fuzzy: bool = False,
        limit: int = 100,
    ) -> list[tuple[str, int]]:
        idx = getattr(self, "_chars_by_syl", None)
        if idx is None:
            return []
        out: list[tuple[str, int]] = []
        if not fuzzy:
            out.extend(idx.get(syl, ()))
            if last_prefix and syl:
                keys = getattr(self, "_chars_syl_sorted", [])
                lo, hi = self._prefix_range(keys, syl)
                for k in keys[lo:hi]:
                    if k != syl:
                        out.extend(idx[k])
        else:
            from .pinyin import fold_fuzzy
            fs = fold_fuzzy(syl)
            for k, lst in idx.items():
                fk = fold_fuzzy(k)
                if fk == fs or (last_prefix and fk.startswith(fs)):
                    out.extend(lst)
        best: dict[str, int] = {}
        for ch, fr in out:
            if ch not in best or fr > best[ch]:
                best[ch] = fr
        merged = sorted(best.items(), key=lambda t: -t[1])
        return merged[:limit]
