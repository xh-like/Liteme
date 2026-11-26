# LiteIME 轻输入法

一套核心算法、两端可用的中文输入法原型：**Windows 桌面输入工具**（开箱即用）+ **Android 系统输入法**（完整工程源码）。

支持主流输入方式：

| 输入方式 | 说明 |
|---------|------|
| **全拼** | 智能切分（`xian` → 先 / 西安）、简拼（`zg` → 中国）、前缀补全、模糊音 |
| **双拼** | 小鹤双拼 / 微软双拼 / 自然码 三方案 |
| **手写** | 按笔画顺序书写，笔画方向序列 + 加权编辑距离识别，9,574 字 |
| **联想** | 上屏后自动推荐后续常用词 |

内置词库：44,440 单字读音 · 413,331 词组 · 词频排序 · 口语高频词加权。

## 目录结构

```
LiteIME/
├── core/            核心引擎（Python：Windows 端直接引用）
├── windows/         Windows 输入工具（tkinter，零第三方依赖）
├── android/         Android 输入法工程（Kotlin，Android Studio 可直接构建）
├── data/            共享词库数据（两端使用同一份）
├── tools/           数据构建脚本（可复现）
├── tests/           引擎测试（27 项）
└── docs/            算法规范（两端实现一一对应）
```

## Windows 快速开始

要求：Python 3.10+（无需安装任何第三方库）。

```bash
python windows/main.py
```

功能面板：

- **全局模式**（默认开启）：安装低级键盘钩子，在任意窗口打字即弹出悬浮候选栏；
  数字键选字、空格上屏首选、回车上屏原始编码、`-`/`=` 或 PgUp/PgDn 翻页。
- **Shift** 单击切换中/英，**Ctrl+Space** 亦可切换。
- **方案切换**：全拼 / 小鹤双拼 / 微软双拼 / 自然码。
- **模糊音**：z/zh、c/ch、s/sh、n/l、前后鼻音。
- **手写板**：画布按笔画书写，自动出候选，点击即上屏。
- **面板内输入**：不依赖全局钩子的独立体验入口。
- **上屏方式**：剪贴板粘贴（默认）/ Unicode 直输。

> 提示：先打开记事本等可输入窗口再体验上屏；若杀毒软件拦截全局键盘钩子，请允许或改用面板内输入。

## Android 构建

要求：Android Studio（Ladybug 或更新）+ JDK 17。

1. 用 Android Studio 打开 `android/` 目录；
2. 等待 Gradle 同步（首次会下载依赖）；
3. Run `app` 到真机 / 模拟器；
4. 在应用中按提示：**启用 LiteIME 输入法 → 切换到 LiteIME**；
5. 打开任意输入框即可使用；键盘右下角「手写」进入手写模式，「中/英」切换语言。

命令行构建（已配置 SDK 时）：

```bash
cd android && ./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 云端编译（无需本地安装 JDK / Android SDK / PyInstaller）

项目已内置 GitHub Actions 工作流 `.github/workflows/build.yml`，在云端同时编译两个产物：

- `LiteIME-APK` → `app-debug.apk`（Android）
- `LiteIME-EXE` → `LiteIME.exe`（Windows 单文件）

使用步骤：

1. 把 `LiteIME/` 目录作为仓库根目录上传到 GitHub（新建仓库 → 上传全部文件）；
2. 打开仓库 **Actions** 页，允许 workflows；
3. 选择 **Build LiteIME (APK + EXE)** → **Run workflow**（或 push 到 `main`/`master` 自动触发）；
4. 运行完成后，在每个 job 页面底部 **Artifacts** 下载 APK 和 EXE。

工作流在云端使用 Ubuntu + JDK17 + Android SDK35 + Gradle8.9 编译安卓端，
使用 Windows + Python3.13 + PyInstaller 编译电脑端；词库已随源码提交，无需额外下载。

> 当前会话按你的要求没有下载任何构建工具，因此本地不产生二进制产物；
> 用上面的云端工作流即可得到两个可安装文件。

## Windows 本地自编译（可选，需本机已有 Python + tcl/tk）

```bash
python -m pip install pyinstaller
python -m PyInstaller --noconfirm --onefile --noconsole --name LiteIME \
  --add-data "data;data" --distpath dist --workpath build windows/main.py
# 产物：dist/LiteIME.exe
```

## Android 本地自编译（可选，需本机已有 Android Studio）

```bash
cd android && ./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 测试

```bash
python tests/test_engine.py
```

覆盖：全拼切分/简拼/多路径、模糊音、三种双拼方案、手写方向分类与识别、联想预测（27 项全部通过）。

## 重新构建词库（可选）

```bash
python tools/build_data.py          # 从开源语料下载并编译 data/
python tools/build_data.py --no-net # 仅用 tools/_cache/ 本地缓存
```

## 设计说明

- 两端共享 `data/` 词库与 `docs/engine-spec.md` 算法规范；Python 与 Kotlin 实现保持同名同义模块。
- Windows 端零第三方依赖：键盘钩子用 ctypes 直调 `SetWindowsHookEx`，上屏用剪贴板 + `SendInput`。
- Android 端为标准 `InputMethodService`，自绘键盘/候选条/手写画布，无第三方 UI 依赖。
- 手写识别采用笔画方向序列匹配（非深度学习），优点是零模型、离线、可解释；对规范书写（笔画数正确）识别较好，连笔/草书有限。后续可替换为 CNN/CRNN 模型而不影响接口。

## 已知限制（原型）

- 无用户词频自适应 / 云词库 / 皮肤；双拼词组输入按 2 键一组解码。
- Windows 端候选栏位置固定在屏幕中上部（未跟随光标）；全局钩子对部分以管理员权限运行的窗口无效。
- 手写识别对潦草连笔准确率有限；建议按规范笔画数书写。
- Android 工程未在本机编译验证（无 Android SDK），代码按 AGP 8.5 / Kotlin 2.0 规范编写。
