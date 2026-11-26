#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输入引擎：音节切分 + 候选生成 + 分页。

公开 API（Android Kotlin 移植保持同名同义）：
    engine = PinyinEngine(dictionary, scheme="quanpin")
    r = engine.query("nihao")
    r.candidates  -> ['你好', '你号', ...]
    r.segments    -> ['ni', 'hao']
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .dictionary import Dictionary, WordEntry
from .pinyin import ALIASES, SYLLABLES, normalize_input, syl_eq, syl_has_prefix
from .shuangpin import SCHEMES, decode_shuangpin

Seg = tuple[str, bool]  # (音节, 是否前缀/不完整)

# 全部音节前缀集合（用于判断 "zh" 这类不完整输入）
_SYL_PREFIXES: set[str] = set()
for _s in SYLLABLES:
    for _i in range(1, len(_s) + 1):
        _SYL_PREFIXES.add(_s[:_i])
for _a in ALIASES.values():
    for _i in range(1, len(_a) + 1):
        _SYL_PREFIXES.add(_a[:_i])


@dataclass
class QueryResult:
    raw: str
    segments: list[str] = field(default_factory=list)
    candidates: list[str] = field(default_factory=list)
    total: int = 0
    page: int = 0
    page_size: int = 9

    @property
    def has_more(self) -> bool:
        return (self.page + 1) * self.page_size < self.total


class PinyinEngine:
    def __init__(
        self,
        dictionary: Dictionary,
        scheme: str = "quanpin",
        fuzzy: bool = False,
        page_size: int = 9,
    ) -> None:
        self.dict = dictionary
        self.scheme = scheme
        self.fuzzy = fuzzy
        self.page_size = page_size

    # ------------------------------------------------------------ 切分
    def _segment_quanpin(self, group: str) -> list[list[Seg]]:
        """全拼音节切分，返回按优先级排序的多条路径（允许末尾不完整音节）。"""
        if not group:
            return []
        n = len(group)
        # memo[i] -> 从 i 到结尾的所有切分路径（按“段数少、首段长”优先）
        memo: dict[int, list[list[Seg]]] = {}

        def walk(i: int) -> list[list[Seg]]:
            if i in memo:
                return memo[i]
            if i == n:
                return [[]]
            paths: list[list[Seg]] = []
            # 1) 尽量匹配完整音节（含别名）
            for L in range(6, 0, -1):
                piece = group[i:i + L]
                if len(piece) < L:
                    continue
                candidates_syl: list[str] = []
                if piece in SYLLABLES:
                    candidates_syl.append(piece)
                if piece in ALIASES and ALIASES[piece] not in candidates_syl:
                    candidates_syl.append(ALIASES[piece])
                for syl in candidates_syl:
                    for tail in walk(i + L):
                        paths.append([(syl, False)] + tail)
            # 2) 尾部剩余作为不完整音节前缀（如 "zh"）
            rest = group[i:]
            if rest in _SYL_PREFIXES:
                paths.append([(rest, True)])
            # 3) 简拼：把单字母当作声母/音节前缀继续切（zg -> z+g）
            if i + 1 <= n:
                for tail in walk(i + 1):
                    seg = [(group[i], True)] + tail
                    if seg not in paths:
                        paths.append(seg)
            # 排序：段数少优先，其次完整切分优先，再按首段长
            paths.sort(key=lambda p: (
                len(p),
                sum(1 for _, isp in p if isp),
                -len(p[0][0]),
            ))
            paths = paths[:16]
            memo[i] = paths
            return paths

        return walk(0)

    def _all_paths(self, raw: str) -> list[list[Seg]]:
        raw = normalize_input(raw)
        if not raw:
            return []
        if self.scheme != "quanpin":
            return decode_shuangpin(raw, self.scheme)
        groups = [g for g in raw.split("'") if g]
        if not groups:
            return []
        # 多组（用 ' 分隔）：按组拼接路径
        group_paths = [self._segment_quanpin(g) for g in groups]
        paths: list[list[Seg]] = [[]]
        for gp in group_paths:
            new_paths: list[list[Seg]] = []
            for base in paths:
                for seg in gp:
                    new_paths.append(base + seg)
            paths = new_paths[:12]
        return paths

    # ------------------------------------------------------------ 查询
    def query(self, raw: str, page: int = 0) -> QueryResult:
        raw = normalize_input(raw)
        result = QueryResult(raw=raw, page=page, page_size=self.page_size)
        if not raw:
            return result

        paths = self._all_paths(raw)
        tiers: list[list[str]] = [[] for _ in range(5)]
        seen: set[str] = set()

        def push(tier: int, text: str) -> None:
            if text and text not in seen:
                seen.add(text)
                tiers[tier].append(text)

        best_segments: list[str] | None = None
        completions: list = []   # (entry, rank) 补全词，延后收集
        for pi, path in enumerate(paths):
            if not path:
                continue
            if best_segments is None:
                best_segments = [s for s, _ in path]
            syls = [s for s, _ in path]
            last_prefix = path[-1][1]
            all_prefix = all(p for _, p in path)

            # 精确匹配（长度一致）与补全（输入是长词前缀）
            exact = self.dict.find_words(
                syls, last_prefix=last_prefix, all_prefix=False,
                fuzzy=self.fuzzy, allow_longer=True, limit=120,
            )
            for e, rank in exact:
                if rank == 0:
                    push(0 if pi == 0 else 1, e.word)
                elif rank == 1:
                    completions.append(e)

            # 单字（主路径优先进首页，其余单字进次级）
            if len(path) == 1:
                syl, isp = path[0]
                chars = self.dict.find_chars(
                    syl, last_prefix=isp, fuzzy=self.fuzzy, limit=60
                )
                for i, (ch, _f) in enumerate(chars):
                    push((0 if pi == 0 else 1) if i < 5 else 2, ch)
            elif not all_prefix and len(path) == 2:
                for w in self._char_chain(syls, last_prefix):
                    push(1, w)

            # 简拼（每个音节取前缀）
            if all_prefix and len(path) >= 2:
                abbr = self.dict.find_words(
                    syls, last_prefix=False, all_prefix=True,
                    fuzzy=self.fuzzy, allow_longer=False, limit=120,
                )
                for e, _rank in abbr:
                    push(3, e.word)

        for e in completions:
            push(2, e.word)

        collected: list[str] = []
        for t in tiers:
            collected.extend(t)
        result.segments = best_segments or []
        result.total = len(collected)
        start = page * self.page_size
        result.candidates = collected[start:start + self.page_size]
        return result

    def _char_chain(self, syls: list[str], last_prefix: bool) -> list[str]:
        pools: list[list[str]] = []
        for i, syl in enumerate(syls):
            is_last = i == len(syls) - 1
            chars = self.dict.find_chars(
                syl, last_prefix=is_last and last_prefix,
                fuzzy=self.fuzzy, limit=8,
            )
            pools.append([c for c, _ in chars])
        if not pools or any(not p for p in pools):
            return []
        out: list[str] = []
        # 只展开前 8×8 种组合
        for a in pools[0][:8]:
            if len(pools) == 1:
                out.append(a)
            else:
                for b in pools[1][:8]:
                    out.append(a + b)
        return out

    # ------------------------------------------------------------ 辅助
    def segment_display(self, raw: str) -> str:
        """把输入格式化为 "ni'hao" 形式用于预编辑区。"""
        paths = self._all_paths(raw)
        if not paths:
            return normalize_input(raw)
        return " ".join(s for s, _ in paths[0])
