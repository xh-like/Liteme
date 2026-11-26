#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LiteIME 数据构建脚本（可复现）
==============================
从开源语料下载原始数据，编译出 Windows / Android 两端共享的三个数据文件：

  data/chars_pinyin.txt  单字拼音 + 词频
  data/words.txt         词组拼音 + 词频
  data/strokes.txt       汉字笔画方向序列（手写识别用）

数据来源（均为开源许可，详见 docs/engine-spec.md）：
  - mozillazg/pinyin-data        (MIT)   单字拼音
  - mozillazg/phrase-pinyin-data (MIT)   词组拼音
  - fxsjy/jieba                  (Apache-2.0)  词频
  - skishore/makemeahanzi        (LGPL)  笔画中线(medians)

用法:
  python tools/build_data.py           # 下载并构建
  python tools/build_data.py --no-net  # 仅用本地缓存构建
"""
from __future__ import annotations

import json
import math
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", "_cache")
OUT = os.path.join(ROOT, "data")

SOURCES = {
    "char_pinyin.txt": "https://raw.githubusercontent.com/mozillazg/pinyin-data/master/pinyin.txt",
    "phrase_pinyin.txt": "https://raw.githubusercontent.com/mozillazg/phrase-pinyin-data/master/pinyin.txt",
    "large_pinyin.txt": "https://raw.githubusercontent.com/mozillazg/phrase-pinyin-data/master/large_pinyin.txt",
    "jieba_dict.txt": "https://raw.githubusercontent.com/fxsjy/jieba/master/jieba/dict.txt",
    "graphics.txt": "https://raw.githubusercontent.com/skishore/makemeahanzi/master/graphics.txt",
}

# ---------------------------------------------------------------- 下载

def download(name: str, url: str, allow_net: bool = True) -> str:
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        print(f"  [cache] {name} ({os.path.getsize(path)//1024} KB)")
        return path
    if not allow_net:
        raise SystemExit(f"缺少缓存文件 {name}，且指定了 --no-net")
    print(f"  [down]  {name} <- {url}")
    # 优先 curl（对代理环境更稳），退回 urllib
    import subprocess
    ok = False
    for attempt in range(3):
        r = subprocess.run(
            ["curl", "-sL", "--max-time", "180", "-o", path, url],
            capture_output=True,
        )
        if r.returncode == 0 and os.path.exists(path) and os.path.getsize(path) > 0:
            ok = True
            break
        time.sleep(2 * (attempt + 1))
    if not ok:
        req = urllib.request.Request(url, headers={"User-Agent": "LiteIME-build/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=120) as resp, open(path, "wb") as f:
                while True:
                    chunk = resp.read(1 << 16)
                    if not chunk:
                        break
                    f.write(chunk)
        except Exception as e:  # noqa: BLE001
            raise SystemExit(f"下载失败 {name}: {e}")
    print(f"          {os.path.getsize(path)//1024} KB")
    return path


# ---------------------------------------------------------------- 拼音工具

_TONE_MAP = {
    "ā": "a", "á": "a", "ǎ": "a", "à": "a",
    "ē": "e", "é": "e", "ě": "e", "è": "e",
    "ī": "i", "í": "i", "ǐ": "i", "ì": "i",
    "ō": "o", "ó": "o", "ǒ": "o", "ò": "o",
    "ū": "u", "ú": "u", "ǔ": "u", "ù": "u",
    "ǖ": "v", "ǘ": "v", "ǚ": "v", "ǜ": "v", "ü": "v",
    "ń": "n", "ň": "n", "ǹ": "n", "ḿ": "m",
}


def strip_tones(s: str) -> str:
    out = []
    for ch in s.lower().strip():
        if ch in _TONE_MAP:
            out.append(_TONE_MAP[ch])
        elif "a" <= ch <= "z" or ch in "' ":
            out.append(ch)
    return "".join(out)


# ---------------------------------------------------------------- 解析

def parse_char_pinyin(path: str) -> dict[str, list[str]]:
    """U+4E00: yī  # 一  ->  {'一': ['yi']}"""
    res: dict[str, list[str]] = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            # U+4E00: yī  # 一
            m = re.match(r"U\+([0-9A-Fa-f]+):\s*([^#]+)", line)
            if not m:
                continue
            char = chr(int(m.group(1), 16))
            readings = []
            for part in m.group(2).split(","):
                py = strip_tones(part)
                if py and py not in readings:
                    readings.append(py)
            if readings:
                res[char] = readings
    return res


def parse_phrase_pinyin(*paths: str) -> dict[str, list[str]]:
    """你好: nǐ hǎo  ->  {'你好': ['ni', 'hiao'...]} 存为音节列表"""
    res: dict[str, list[str]] = {}
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or ":" not in line:
                    continue
                word, py = line.split(":", 1)
                word = word.strip()
                if not word or not re.fullmatch(r"[一-鿿]+", word):
                    continue
                syllables = [strip_tones(x) for x in py.split() if strip_tones(x)]
                if len(syllables) == len(word):
                    res[word] = syllables  # 后面的文件覆盖前面的（更权威）
    return res


# 口语高频词加权（语料词频偏低但实际输入极频繁），模拟真实输入法的使用频率
USAGE_BOOST = {
    "你好", "我们", "你们", "什么", "怎么", "因为", "所以", "但是", "现在",
    "可以", "没有", "这个", "那个", "知道", "自己", "还是", "就是", "如果",
    "谢谢", "好的", "一下", "时候", "觉得", "应该", "不是", "这样", "那样",
    "今天", "明天", "昨天", "一个", "一样", "看看", "对的", "再见", "加油",
    "没关系", "不好意思", "没问题", "怎么样", "为什么", "这么", "那么",
    "开始", "已经", "还有", "大家", "请问", "hello", "谢谢", "哈哈",
}


def parse_freq(path: str) -> dict[str, int]:
    res: dict[str, int] = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) >= 2:
                try:
                    res[parts[0]] = int(parts[1])
                except ValueError:
                    continue
    return res


# ---------------------------------------------------------------- 笔画方向
# 与 core/handwriting.classify_stroke 共用同一算法（数据为 y 轴向上）

def _classify_stroke(median: list[list[float]]) -> str:
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from core.handwriting import classify_stroke
    # makemeahanzi 中线坐标系约 1024 单位、y 轴向上
    return classify_stroke([(p[0], p[1]) for p in median], y_up=True, dot_thresh=48.0)


def parse_strokes(path: str) -> dict[str, str]:
    """makemeahanzi graphics.txt: 每行 JSON {character, medians, strokes,...}"""
    res: dict[str, str] = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            char = item.get("character", "")
            medians = item.get("medians") or []
            if not char or not medians:
                continue
            seq = "".join(_classify_stroke(m) for m in medians)
            res[char] = seq
    return res


# ---------------------------------------------------------------- 主流程

def main() -> None:
    allow_net = "--no-net" not in sys.argv
    os.makedirs(OUT, exist_ok=True)

    print("==> 下载数据源")
    p_char = download("char_pinyin.txt", SOURCES["char_pinyin.txt"], allow_net)
    p_phrase = download("phrase_pinyin.txt", SOURCES["phrase_pinyin.txt"], allow_net)
    p_large = download("large_pinyin.txt", SOURCES["large_pinyin.txt"], allow_net)
    p_freq = download("jieba_dict.txt", SOURCES["jieba_dict.txt"], allow_net)
    p_graph = download("graphics.txt", SOURCES["graphics.txt"], allow_net)

    print("==> 解析")
    char_py = parse_char_pinyin(p_char)
    print(f"  单字拼音: {len(char_py)}")
    phrase_py = parse_phrase_pinyin(p_large, p_phrase)
    print(f"  词组拼音: {len(phrase_py)}")
    freq = parse_freq(p_freq)
    print(f"  词频:     {len(freq)}")
    strokes = parse_strokes(p_graph)
    print(f"  笔画序列: {len(strokes)}")

    # ---- chars_pinyin.txt :  char  py1[,py2]  freq
    char_freq = {}
    for w, v in freq.items():
        if len(w) == 1:
            char_freq[w] = v
    path = os.path.join(OUT, "chars_pinyin.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# LiteIME 单字表  char<TAB>pinyin(逗号多音,无声调,ü->v)<TAB>freq\n")
        for char, readings in sorted(char_py.items()):
            f.write(f"{char}\t{','.join(readings)}\t{char_freq.get(char, 0)}\n")
    print(f"  写出 {path} ({os.path.getsize(path)//1024} KB)")

    # ---- words.txt : word<TAB>syllables<TAB>freq
    path = os.path.join(OUT, "words.txt")
    n = 0
    with open(path, "w", encoding="utf-8") as f:
        f.write("# LiteIME 词表  word<TAB>py(音节空格分隔)<TAB>freq\n")
        for word, syl in sorted(phrase_py.items(), key=lambda kv: -freq.get(kv[0], 0)):
            fr = freq.get(word)
            if fr is None:
                # 未登录词给低兜底词频，保证排在真实词频之后
                char_sum = sum(char_freq.get(c, 1) for c in word)
                fr = min(200, 30 + char_sum // (len(word) * 400))
            if word in USAGE_BOOST:
                fr = max(fr, 2_000_000)   # 口语高频词置顶
            f.write(f"{word}\t{' '.join(syl)}\t{fr}\n")
            n += 1
        # jieba 中有拼音可补的高词频词？没有拼音无法补，跳过
    print(f"  写出 {path} ({os.path.getsize(path)//1024} KB, {n} 词)")

    # ---- strokes.txt : char<TAB>方向序列
    path = os.path.join(OUT, "strokes.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# LiteIME 笔画序列  char<TAB>directions(h横 s竖 p撇 n捺/点 z折)\n")
        for char, seq in sorted(strokes.items(), key=lambda kv: ord(kv[0])):
            f.write(f"{char}\t{seq}\n")
    print(f"  写出 {path} ({os.path.getsize(path)//1024} KB)")

    print("==> 完成")


if __name__ == "__main__":
    main()
