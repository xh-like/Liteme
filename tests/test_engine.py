#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核心引擎测试：python tests/test_engine.py"""
from __future__ import annotations

import os
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from core.dictionary import Dictionary
from core.engine import PinyinEngine
from core.handwriting import HandwritingRecognizer, classify_stroke
from core.predictor import Predictor
from core.shuangpin import decode_shuangpin

DATA = os.path.join(ROOT, "data")

PASS = 0
FAIL = 0


def check(name: str, cond: bool, detail: str = "") -> None:
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}  {detail}")


def main() -> None:
    t0 = time.time()
    print("加载词典 ...")
    d = Dictionary.load(DATA)
    print(f"  {time.time() - t0:.2f}s  单字{len(d.chars)} 词{len(d.words)} 笔画{len(d.strokes)}")
    eng = PinyinEngine(d)

    print("\n== 全拼查询 ==")
    r = eng.query("nihao")
    check("nihao -> 你好", "你好" in r.candidates, str(r.candidates[:5]))
    check("nihao segments", r.segments == ["ni", "hao"], str(r.segments))

    r = eng.query("zhongguo")
    check("zhongguo -> 中国", "中国" in r.candidates, str(r.candidates[:5]))

    r = eng.query("pinyin")
    check("pinyin 有候选", len(r.candidates) > 0, str(r.candidates[:5]))

    r = eng.query("xian")
    check("xian 多路径(先/西安)", "先" in r.candidates and ("西安" in r.candidates or "西" in r.candidates),
          str(r.candidates[:8]))

    r = eng.query("zh")   # 前缀
    check("zh 前缀有字候选", len(r.candidates) > 0, str(r.candidates[:5]))

    r = eng.query("ni'hao")  # 分隔符
    check("ni'hao 分隔", "你好" in r.candidates, str(r.candidates[:5]))

    r = eng.query("zg")   # 简拼
    check("zg 简拼 -> 中国", "中国" in r.candidates, str(r.candidates[:8]))

    r = eng.query("nv")
    check("nv -> 女", "女" in r.candidates, str(r.candidates[:5]))

    r = eng.query("lv")
    check("lv -> 绿/吕", "绿" in r.candidates or "吕" in r.candidates, str(r.candidates[:5]))

    r = eng.query("hello")
    check("hello 不崩", True)

    # 模糊音
    eng_f = PinyinEngine(d, fuzzy=True)
    r = eng_f.query("zongguo")
    check("模糊音 zongguo ~ 中国", "中国" in r.candidates, str(r.candidates[:5]))

    print("\n== 双拼 ==")
    # 小鹤：中=vs  国=go  你好=ni hc(好=h+k? hao: h+ao=C -> hc)
    p = decode_shuangpin("vsgo", "xiaohe")
    check("小鹤 vsgo 有路径", len(p) > 0, str(p))
    engx = PinyinEngine(d, scheme="xiaohe")
    r = engx.query("vsgo")
    check("小鹤 vsgo -> 中国", "中国" in r.candidates, str(r.candidates[:5]))

    r = engx.query("nihc")
    check("小鹤 nihc -> 你好", "你好" in r.candidates, str(r.candidates[:5]))

    # 微软：中=vs 国=go（guo=g+uo=go） 零声母 a=oa
    engm = PinyinEngine(d, scheme="mspy")
    r = engm.query("vsgo")
    check("微软 vsgo -> 中国", "中国" in r.candidates, str(r.candidates[:5]))
    p = decode_shuangpin("oa", "mspy")
    check("微软 oa -> a", any(seg[0][0] == "a" for seg in p), str(p))

    # 自然码：请=q+y -> qing
    p = decode_shuangpin("qy", "ziranma")
    check("自然码 qy -> qing", any(seg[0][0] == "qing" for seg in p), str(p))
    engz = PinyinEngine(d, scheme="ziranma")
    r = engz.query("qy")
    check("自然码 qy 有候选", len(r.candidates) > 0, str(r.candidates[:5]))

    print("\n== 手写识别 ==")
    check("classify 横", classify_stroke([(0, 0), (50, 2), (100, 0)]) == "h")
    check("classify 竖", classify_stroke([(0, 0), (2, 50), (0, 100)]) == "s")
    check("classify 撇", classify_stroke([(100, 0), (50, 50), (0, 100)]) == "p")
    check("classify 捺", classify_stroke([(0, 0), (50, 50), (100, 100)]) == "n")

    hw = HandwritingRecognizer(d)
    # 「一」：一横
    res = hw.recognize([[(10, 50), (90, 50)]])
    check("手写「一」", "一" in res[:5], str(res[:5]))
    # 「十」：横 + 竖
    res = hw.recognize([[(10, 50), (90, 50)], [(50, 10), (50, 90)]])
    check("手写「十」", "十" in res[:5], str(res[:5]))
    # 「人」：撇 + 捺
    res = hw.recognize([[(60, 10), (30, 60), (10, 90)], [(45, 45), (70, 70), (90, 95)]])
    check("手写「人」", "人" in res[:8], str(res[:8]))

    print("\n== 联想 ==")
    pred = Predictor(d)
    s = pred.predict("我")
    check("联想「我」-> 我们", "我们" in s, str(s))

    print(f"\n结果: PASS={PASS} FAIL={FAIL}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
