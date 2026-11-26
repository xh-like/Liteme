#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""手写识别：笔画方向分类 + 加权编辑距离匹配。

识别流水线：
  1. 把每一条手写笔迹（点序列）归类为方向类别 h横 s竖 p撇 n捺/点 z折
  2. 与 data/strokes.txt 中的笔画方向序列做加权编辑距离
  3. 按距离 + 笔画数差排序，返回 Top-N 候选

公开 API（Android Kotlin 移植保持同名同义）：
    rec = HandwritingRecognizer(dictionary)
    rec.recognize(strokes)  -> ['大', '太', '犬', ...]
"""
from __future__ import annotations

import math

from .dictionary import Dictionary

# 方向类别
H, S, P, N, Z = "h", "s", "p", "n", "z"
CLASSES = (H, S, P, N, Z)

# 替换代价矩阵：对角线为 0，易混笔画代价低
_SUB_COST = {
    (H, S): 0.75, (H, P): 1.0, (H, N): 1.0, (H, Z): 0.85,
    (S, P): 1.0, (S, N): 1.0, (S, Z): 0.85,
    (P, N): 0.55, (P, Z): 0.8,
    (N, Z): 0.85,
}
_INS_COST = 0.62   # 多写一笔
_DEL_COST = 0.62   # 少写一笔


def _sub(a: str, b: str) -> float:
    if a == b:
        return 0.0
    return _SUB_COST.get((a, b), _SUB_COST.get((b, a), 1.0))


def classify_stroke(
    points: list[tuple[float, float]],
    y_up: bool = False,
    dot_thresh: float = 14.0,
) -> str:
    """把一条笔迹归类为 h/s/p/n/z。

    points: 点序列；y_up=True 表示 y 轴向上（makemeahanzi 数据），
    False 表示 y 轴向下（屏幕画布坐标）。
    dot_thresh: 判定"点"的弦长阈值（按坐标尺度传入）。
    """
    if len(points) < 2:
        return N
    # 去重相邻重复点
    pts = [points[0]]
    for p in points[1:]:
        if abs(p[0] - pts[-1][0]) > 0.5 or abs(p[1] - pts[-1][1]) > 0.5:
            pts.append(p)
    if len(pts) < 2:
        return N

    x0, y0 = pts[0]
    x1, y1 = pts[-1]
    dx, dy = x1 - x0, y1 - y0
    if y_up:
        dy = -dy          # 统一转换到 y 向下的判定空间
    chord = math.hypot(dx, dy)

    # 净转角：前半段方向 vs 后半段方向（折笔判定）
    mid = len(pts) // 2
    v1 = (pts[mid][0] - pts[0][0], pts[mid][1] - pts[0][1])
    v2 = (pts[-1][0] - pts[mid][0], pts[-1][1] - pts[mid][1])
    l1, l2 = math.hypot(*v1), math.hypot(*v2)
    if l1 < 1e-6 or l2 < 1e-6:
        net_turn = 0.0
    else:
        cosv = max(-1.0, min(1.0, (v1[0] * v2[0] + v1[1] * v2[1]) / (l1 * l2)))
        net_turn = math.acos(cosv)

    arc = 0.0
    for i in range(1, len(pts)):
        arc += math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
    curl = arc / chord if chord > 1e-6 else 3.0

    if net_turn > 1.05 or curl > 1.9:
        return Z
    if chord < dot_thresh and arc < dot_thresh * 1.9:
        return N          # 点
    if abs(dx) >= 1.8 * abs(dy):
        return H if dx >= 0 else P
    if abs(dy) >= 1.8 * abs(dx):
        return S
    if dy > 0:
        return N if dx > 0 else P
    return H if dx > 0 else P


class HandwritingRecognizer:
    def __init__(self, dictionary: Dictionary) -> None:
        self.dict = dictionary
        # 按笔画数分桶，加速候选裁剪
        self._by_len: dict[int, list[tuple[str, str]]] = {}
        for ch, seq in dictionary.strokes.items():
            self._by_len.setdefault(len(seq), []).append((ch, seq))

    def recognize(
        self,
        strokes: list[list[tuple[float, float]]],
        top_n: int = 10,
    ) -> list[str]:
        if not strokes:
            return []
        seq = "".join(classify_stroke(s) for s in strokes)
        n = len(seq)

        scored: list[tuple[float, str]] = []
        for length in range(max(1, n - 2), n + 3):
            for ch, target in self._by_len.get(length, ()):
                d = self._distance(seq, target)
                # 笔画数差做轻微惩罚
                d += 0.08 * abs(len(target) - n)
                scored.append((d, ch))

        scored.sort(key=lambda t: (t[0], -self.dict.char_freq.get(t[1], 0)))
        out: list[str] = []
        for _d, ch in scored:
            if ch not in out:
                out.append(ch)
            if len(out) >= top_n:
                break
        return out

    @staticmethod
    def _distance(a: str, b: str) -> float:
        """加权编辑距离（小矩阵 DP）。"""
        m, n = len(a), len(b)
        prev = [j * _INS_COST for j in range(n + 1)]
        for i in range(1, m + 1):
            cur = [i * _DEL_COST] + [0.0] * n
            for j in range(1, n + 1):
                cur[j] = min(
                    prev[j - 1] + _sub(a[i - 1], b[j - 1]),
                    prev[j] + _DEL_COST,
                    cur[j - 1] + _INS_COST,
                )
            prev = cur
        return prev[n]
