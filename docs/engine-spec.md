# LiteIME 核心算法规范

本规范定义 Windows 端（Python）与 Android 端（Kotlin）共享的输入法核心算法。
两端实现一一对应，模块映射：

| 算法 | Python (`core/`) | Kotlin (`android/.../core/`) |
|------|------------------|------------------------------|
| 拼音工具 | `pinyin.py` | `Pinyin.kt` |
| 双拼解码 | `shuangpin.py` | `Shuangpin.kt` |
| 词典检索 | `dictionary.py` | `Dictionary.kt` |
| 输入引擎 | `engine.py` | `PinyinEngine.kt` |
| 手写识别 | `handwriting.py` | `HandwritingRecognizer.kt` |
| 联想预测 | `predictor.py` | `Predictor.kt` |

## 1. 数据文件（两端共享）

由 `tools/build_data.py` 从开源语料构建，输出到 `data/` 并复制到 Android assets：

- `chars_pinyin.txt` — `char<TAB>读音(逗号分隔,无声调,ü→v)<TAB>词频`
- `words.txt` — `word<TAB>音节(空格分隔)<TAB>词频`（口语高频词做了加权）
- `strokes.txt` — `char<TAB>笔画方向序列`（h横 s竖 p撇 n捺/点 z折）

数据来源与许可：

| 语料 | 来源 | 许可 |
|------|------|------|
| 单字拼音 | mozillazg/pinyin-data | MIT |
| 词组拼音 | mozillazg/phrase-pinyin-data | MIT |
| 词频 | fxsjy/jieba | Apache-2.0 |
| 笔画中线 | skishore/makemeahanzi | LGPL |

## 2. 全拼切分

输入规范化：小写字母 + `'` 分隔符（`v` 表示 ü）。

对每个 `'` 分隔的字母组做动态规划切分，路径按 `(段数少, 完整切分优先, 首段长)` 排序：

1. 完整音节匹配（含零声母别名：`in→yin`、`ua→wa`、`v→yu` 等）
2. 尾部剩余若为某音节的真前缀（如 `zh`）→ 记为不完整音节
3. 单字母简拼继续切（`zg → z+g`），支持首字母缩写输入

多路径示例：`xian` → `[xian]`（先/现）与 `[xi, an]`（西安）。

## 3. 候选分层

对每条切分路径收集候选，按以下层次合并去重（先出现者优先）：

- **L0** 主路径：整词精确匹配（音节数一致）+ 高频单字（前 5）
- **L1** 次路径：整词精确匹配 + 单字/双音节字链
- **L2** 补全词（输入是长词的前缀）+ 其余单字
- **L3** 简拼匹配（每个输入音节是词音节的前缀，且音节数一致）

排序键：`match_rank`（0 精确 / 1 补全 / 2 简拼）→ 词频降序。

## 4. 模糊音（可选开启）

折叠规则：`zh→z, ch→c, sh→s, n→l, ang→an, eng→en, ing→in, uang→uan, ong→un`。
查询与词典音节折叠后比较。

## 5. 双拼解码

每 2 键 1 音节（末尾单键为前缀）。第一键→声母（`v/i/u → zh/ch/sh`），
第二键→韵母（一键多韵母时逐一拼合、取能构成合法音节者）。零声母：

- **小鹤 / 自然码**：单韵母重复键（`aa`）；双字母韵母按原拼写（`ai`）；三字母韵母首字母+韵母键（`ang→ah`）
- **微软**：`o` + 韵母键（`a→oa, ai→ol`）

键位表见 `core/shuangpin.py`，来源 flypy.cc / shuangpin.xyz / laidazi.com。

## 6. 手写识别

1. 每条笔迹归类为方向类别。以起终点弦向为主：
   - 弦长小于 `dot_thresh` → 点(n)
   - 前半段/后半段净转角 > 1.05 rad 或弧弦比 > 1.9 → 折(z)
   - 横向主导 → 横(h)/撇(p)，纵向主导 → 竖(s)，斜向按方向 → 捺(n)/撇(p)/提(h)
   - makemeahanzi 数据 y 轴向上，画布 y 轴向下，分别传入 `y_up` 标志
2. 与 `strokes.txt` 的方向序列做加权编辑距离：
   - 替换代价：`p↔n 0.55`（撇捺易混）、`h↔s 0.75`、含 z 0.8~0.85、其余 1.0
   - 插入/删除（多写/少写一笔）0.62
   - 附加 `0.08 × |笔画数差|`
3. 只比较笔画数 ±2 的候选，按距离升序、词频降序返回 Top-N。

## 7. 联想预测

以已上屏文本的末 2 字 / 1 字为前缀，取词典中更长的词，按词频排序，兼取以末字开头的常用词。

## 8. 上屏

- **Windows**：剪贴板写入 + 模拟 Ctrl+V（可选 Unicode 直输），随后恢复原剪贴板
- **Android**：`InputConnection.commitText`；拼音预编辑用 `setComposingText`
