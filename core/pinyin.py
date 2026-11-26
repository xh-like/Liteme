#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""拼音工具：音节表、去声调、模糊音折叠、别名（i/u/v 开头的零声母简拼）。"""
from __future__ import annotations

# 普通话全部标准音节（无声调；ü 写作 v）
SYLLABLES: frozenset[str] = frozenset("""
a o e ai ei ao ou an en ang eng ong
ba bo bai bei bao ban ben bang beng bi bie biao bian bin bing bu
pa po pai pei pao pou pan pen pang peng pi pie piao pian pin ping pu
ma mo me mai mei mao mou man men mang meng mi mie miao miu mian min ming mu
fa fo fei fou fan fen fang feng fu
da de dai dei dao dou dan den dang deng dong di dia die diao diu dian ding du duo dui duan dun
ta te tai tao tou tan tang teng tong ti tie tiao tian ting tu tuo tui tuan tun
na ne nai nei nao nou nan nang neng nong ni nie niao niu nian nin niang ning nu nuo nuan nv nve
la le lai lei lao lou lan lang leng long li lia lie liao liu lian lin liang ling lu luo luan lun lv lve
ga ge gai gei gao gou gan gen gang geng gong gu gua guo guai gui guan gun guang
ka ke kai kei kao kou kan ken kang keng kong ku kua kuo kuai kui kuan kun kuang
ha he hai hei hao hou han hen hang heng hong hu hua huo huai hui huan hun huang
ji jia jie jiao jiu jian jin jiang jing jiong ju jue juan jun
qi qia qie qiao qiu qian qin qiang qing qiong qu que quan qun
xi xia xie xiao xiu xian xin xiang xing xiong xu xue xuan xun
zha zhe zhi zhai zhei zhao zhou zhan zhen zhang zheng zhong zhu zhua zhuo zhuai zhui zhuan zhun zhuang
cha che chi chai chao chou chan chen chang cheng chong chu chua chuo chuai chui chuan chun chuang
sha she shi shai shei shao shou shan shen shang sheng shu shua shuo shuai shui shuan shun shuang
re ri rao rou ran ren rang reng rong ru rua ruo rui ruan run
za ze zi zai zei zao zou zan zen zang zeng zong zu zuo zui zuan zun
ca ce ci cai cao cou can cen cang ceng cong cu cuo cui cuan cun
sa se si sai sao sou san sen sang seng song su suo sui suan sun
ya yo ye yao you yan yin yang ying yong yi yu yue yuan yun
wa wo wai wei wan wen wang weng wu
""".split())

# 零声母别名：用户省略 y/w 直接打 i/u/v 开头的韵母
ALIASES: dict[str, str] = {
    "i": "yi", "ia": "ya", "ie": "ye", "iao": "yao", "iu": "you",
    "ian": "yan", "in": "yin", "iang": "yang", "ing": "ying",
    "iong": "yong", "u": "wu", "ua": "wa", "uo": "wo", "ui": "wei",
    "uai": "wai", "uan": "wan", "un": "wen", "uang": "wang",
    "v": "yu", "ve": "yue", "van": "yuan", "vn": "yun",
}

_TONE_MAP = {
    "ā": "a", "á": "a", "ǎ": "a", "à": "a",
    "ē": "e", "é": "e", "ě": "e", "è": "e",
    "ī": "i", "í": "i", "ǐ": "i", "ì": "i",
    "ō": "o", "ó": "o", "ǒ": "o", "ò": "o",
    "ū": "u", "ú": "u", "ǔ": "u", "ù": "u",
    "ǖ": "v", "ǘ": "v", "ǚ": "v", "ǜ": "v", "ü": "v",
}


def strip_tones(s: str) -> str:
    out = []
    for ch in s.lower():
        if ch in _TONE_MAP:
            out.append(_TONE_MAP[ch])
        elif "a" <= ch <= "z" or ch == "'":
            out.append(ch)
    return "".join(out)


def normalize_input(raw: str) -> str:
    """把用户输入规范成小写字母与分隔符 ' 。"""
    return "".join(c for c in raw.lower() if "a" <= c <= "z" or c in "'v")


def fold_fuzzy(s: str) -> str:
    """模糊音折叠：zh->z / ch->c / sh->s / n->l / 后鼻音->前鼻音。"""
    if s.startswith("zh"):
        s = "z" + s[2:]
    elif s.startswith("ch"):
        s = "c" + s[2:]
    elif s.startswith("sh"):
        s = "s" + s[2:]
    if s.startswith("n"):
        s = "l" + s[1:]
    s = s.replace("ang", "an").replace("eng", "en").replace("ing", "in").replace("uang", "uan").replace("ong", "un")
    return s


def syl_eq(a: str, b: str, fuzzy: bool = False) -> bool:
    if a == b:
        return True
    if fuzzy and fold_fuzzy(a) == fold_fuzzy(b):
        return True
    return False


def syl_has_prefix(target: str, prefix: str, fuzzy: bool = False) -> bool:
    if target.startswith(prefix):
        return True
    if fuzzy:
        ft, fp = fold_fuzzy(target), fold_fuzzy(prefix)
        return ft.startswith(fp)
    return False
