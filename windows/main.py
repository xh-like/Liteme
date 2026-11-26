#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""LiteIME Windows 输入工具 —— 入口。

运行:  python windows/main.py
功能:  悬浮候选栏 / 全局键盘钩子 / 全拼+双拼 / 手写板 / 联想
"""
from __future__ import annotations

import os
import queue
import sys
import tkinter as tk
from tkinter import ttk

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from core.dictionary import Dictionary                     # noqa: E402
from core.engine import PinyinEngine                       # noqa: E402
from core.handwriting import HandwritingRecognizer         # noqa: E402
from core.predictor import Predictor                       # noqa: E402
from windows.ime_hook import GlobalKeyboardHook            # noqa: E402
from windows.sender import Committer                       # noqa: E402
from windows.state import ImeState, KeyProcessor           # noqa: E402

# 数据目录：源码运行时为 <项目>/data；PyInstaller 冻结后为解包目录/data
if getattr(sys, "frozen", False):
    DATA_DIR = os.path.join(getattr(sys, "_MEIPASS", os.path.dirname(sys.executable)), "data")
else:
    DATA_DIR = os.path.join(ROOT, "data")

# 配色（浅色）
C_BG = "#F7F7FA"
C_PANEL = "#FFFFFF"
C_ACCENT = "#3B6FF6"
C_TEXT = "#1F2329"
C_SUB = "#6B7280"
C_CAND_BG = "#FFFFFF"
C_HL = "#EAF0FE"

SCHEME_LABELS = [
    ("quanpin", "全拼"),
    ("xiaohe", "小鹤双拼"),
    ("mspy", "微软双拼"),
    ("ziranma", "自然码"),
]


# ================================================================ 候选栏
class CandidateBar(tk.Toplevel):
    def __init__(self, master: tk.Tk) -> None:
        super().__init__(master)
        self.overrideredirect(True)
        self.attributes("-topmost", True)
        self.configure(bg=C_CAND_BG)
        self._visible = False

        self.preedit = tk.Label(self, text="", font=("Microsoft YaHei UI", 11),
                                fg=C_ACCENT, bg=C_CAND_BG, padx=8, pady=4)
        self.preedit.pack(side=tk.LEFT, fill=tk.Y)

        self.cand_frame = tk.Frame(self, bg=C_CAND_BG)
        self.cand_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        self.info = tk.Label(self, text="", font=("Microsoft YaHei UI", 9),
                             fg=C_SUB, bg=C_CAND_BG, padx=8)
        self.info.pack(side=tk.RIGHT, fill=tk.Y)

        self.cand_buttons: list[tk.Label] = []
        for i in range(9):
            lb = tk.Label(self.cand_frame, text="", font=("Microsoft YaHei UI", 11),
                          fg=C_TEXT, bg=C_CAND_BG, padx=6, pady=4, cursor="hand2")
            lb.pack(side=tk.LEFT)
            lb.bind("<Button-1>", lambda e, idx=i: self.on_pick(idx))
            self.cand_buttons.append(lb)

        self.geometry("+400+300")
        self.withdraw()

    def on_pick(self, idx: int) -> None:
        if self.app:
            self.app.pick_candidate(idx)

    app = None  # 由 App 注入

    def show_for(self, raw: str, segments: str, candidates: list[str],
                 page: int, total: int) -> None:
        self.preedit.configure(text=segments or raw)
        for i, lb in enumerate(self.cand_buttons):
            if i < len(candidates):
                lb.configure(text=f"{i + 1}.{candidates[i]}", bg=C_HL if i == 0 else C_CAND_BG)
            else:
                lb.configure(text="", bg=C_CAND_BG)
        pages = max(1, (total + 8) // 9)
        self.info.configure(text=f"{page + 1}/{pages}" if total else "")
        if not self._visible:
            self._visible = True
            self.deiconify()
        self.lift()
        self._autosize()

    def hide(self) -> None:
        if self._visible:
            self._visible = False
            self.withdraw()

    def _autosize(self) -> None:
        self.update_idletasks()
        w = max(360, self.winfo_reqwidth())
        h = 42
        x = (self.winfo_screenwidth() - w) // 2
        y = int(self.winfo_screenheight() * 0.28)
        self.geometry(f"{w}x{h}+{x}+{y}")


# ================================================================ 手写板
class HandwritingPad(tk.Toplevel):
    def __init__(self, master: tk.Tk, recognizer: HandwritingRecognizer,
                 committer: Committer, on_status) -> None:
        super().__init__(master)
        self.title("LiteIME 手写板")
        self.configure(bg=C_BG)
        self.recognizer = recognizer
        self.committer = committer
        self.on_status = on_status
        self.strokes: list[list[tuple[float, float]]] = []
        self.current: list[tuple[float, float]] = []

        top = tk.Frame(self, bg=C_BG)
        top.pack(fill=tk.X, padx=10, pady=(10, 4))
        tk.Label(top, text="在下方区域书写汉字（按笔画顺序）",
                 font=("Microsoft YaHei UI", 10), fg=C_SUB, bg=C_BG).pack(side=tk.LEFT)
        tk.Button(top, text="退一笔", command=self.undo_stroke,
                  relief=tk.FLAT, bg=C_PANEL).pack(side=tk.RIGHT, padx=2)
        tk.Button(top, text="清空", command=self.clear,
                  relief=tk.FLAT, bg=C_PANEL).pack(side=tk.RIGHT, padx=2)

        self.canvas = tk.Canvas(self, width=320, height=320, bg="#FFFFFF",
                                highlightthickness=1, highlightbackground="#D8DCE6",
                                cursor="crosshair")
        self.canvas.pack(padx=10, pady=6)
        self.canvas.bind("<Button-1>", self._down)
        self.canvas.bind("<B1-Motion>", self._drag)
        self.canvas.bind("<ButtonRelease-1>", self._up)

        self.cand_frame = tk.Frame(self, bg=C_BG)
        self.cand_frame.pack(fill=tk.X, padx=10, pady=(4, 12))
        self.cand_buttons: list[tk.Button] = []
        for i in range(10):
            b = tk.Button(self.cand_frame, text="", width=4, height=2,
                          font=("Microsoft YaHei UI", 14), relief=tk.FLAT,
                          bg=C_PANEL, command=lambda idx=i: self.pick(idx))
            b.grid(row=i // 5, column=i % 5, padx=4, pady=4, sticky="nsew")
            self.cand_frame.columnconfigure(i, weight=1)
            self.cand_buttons.append(b)

        self.geometry("380x520+120+120")

    # ---- 绘制
    def _down(self, e) -> None:
        self.current = [(e.x, e.y)]
        self.canvas.create_line(e.x, e.y, e.x + 1, e.y + 1,
                                fill="#1F2329", width=4, capstyle=tk.ROUND)

    def _drag(self, e) -> None:
        if not self.current:
            return
        x0, y0 = self.current[-1]
        self.canvas.create_line(x0, y0, e.x, e.y, fill="#1F2329",
                                width=4, capstyle=tk.ROUND, joinstyle=tk.ROUND)
        self.current.append((e.x, e.y))

    def _up(self, e) -> None:
        if self.current:
            self.strokes.append(self.current)
            self.current = []
            self._recognize()

    def undo_stroke(self) -> None:
        if self.strokes:
            self.strokes.pop()
            self.clear(keep_strokes=True)
            for s in self.strokes:
                for i in range(1, len(s)):
                    self.canvas.create_line(*s[i - 1], *s[i], fill="#1F2329",
                                            width=4, capstyle=tk.ROUND)
            self._recognize()

    def clear(self, keep_strokes: bool = False) -> None:
        self.canvas.delete("all")
        if not keep_strokes:
            self.strokes = []
        for b in self.cand_buttons:
            b.configure(text="")

    def _recognize(self) -> None:
        cands = self.recognizer.recognize(self.strokes, top_n=10)
        for i, b in enumerate(self.cand_buttons):
            b.configure(text=cands[i] if i < len(cands) else "")

    def pick(self, idx: int) -> None:
        text = self.cand_buttons[idx]["text"]
        if text:
            self.committer.commit(text)
            self.on_status(f"已上屏：{text}")
            self.clear()


# ================================================================ 主程序
class App:
    def __init__(self) -> None:
        print("[1/4] 加载词典 ...")
        self.dictionary = Dictionary.load(DATA_DIR)
        print(f"      单字 {len(self.dictionary.chars)}  词 {len(self.dictionary.words)}"
              f"  笔画 {len(self.dictionary.strokes)}")

        print("[2/4] 初始化引擎 ...")
        self.engine = PinyinEngine(self.dictionary, scheme="quanpin")
        self.recognizer = HandwritingRecognizer(self.dictionary)
        self.predictor = Predictor(self.dictionary)
        self.committer = Committer()
        self.state = ImeState()
        self.proc = KeyProcessor(self.engine, self.state, self.committer)

        print("[3/4] 构建界面 ...")
        self.root = tk.Tk()
        self.root.title("LiteIME 轻输入法")
        self.root.configure(bg=C_BG)
        self.root.geometry("430x560+80+80")
        self.root.attributes("-topmost", True)

        self.events: queue.Queue = queue.Queue()
        self.bar = CandidateBar(self.root)
        self.bar.app = self
        self.pad: HandwritingPad | None = None

        self._build_panel()

        print("[4/4] 启动全局键盘钩子 ...")
        self.hook = GlobalKeyboardHook(self._hook_key)
        self.hook_ok = self.hook.start()
        self.set_status("全局钩子已启动" if self.hook_ok else "全局钩子启动失败（可用面板内输入）")

        self.root.after(50, self._poll_events)
        self.root.protocol("WM_DELETE_WINDOW", self.on_quit)

    # ---------------- 面板 UI
    def _build_panel(self) -> None:
        r = self.root
        header = tk.Frame(r, bg=C_PANEL, padx=16, pady=12)
        header.pack(fill=tk.X, padx=12, pady=(12, 6))
        tk.Label(header, text="LiteIME 轻输入法",
                 font=("Microsoft YaHei UI", 15, "bold"), fg=C_TEXT, bg=C_PANEL).pack(anchor="w")
        tk.Label(header, text="全拼 · 双拼 · 手写 · 联想   |   Shift 切换中/英",
                 font=("Microsoft YaHei UI", 9), fg=C_SUB, bg=C_PANEL).pack(anchor="w", pady=(2, 0))

        card = tk.Frame(r, bg=C_PANEL, padx=16, pady=12)
        card.pack(fill=tk.X, padx=12, pady=6)

        row1 = tk.Frame(card, bg=C_PANEL)
        row1.pack(fill=tk.X, pady=4)
        tk.Label(row1, text="输入方案", font=("Microsoft YaHei UI", 10),
                 fg=C_TEXT, bg=C_PANEL, width=10, anchor="w").pack(side=tk.LEFT)
        self.scheme_var = tk.StringVar(value="全拼")
        cb = ttk.Combobox(row1, textvariable=self.scheme_var, state="readonly",
                          values=[label for _, label in SCHEME_LABELS], width=12)
        cb.pack(side=tk.LEFT)
        cb.bind("<<ComboboxSelected>>", self._on_scheme)

        self.mode_btn = tk.Button(row1, text="中", width=4, font=("Microsoft YaHei UI", 10, "bold"),
                                  fg="#FFFFFF", bg=C_ACCENT, relief=tk.FLAT, command=self.toggle_mode)
        self.mode_btn.pack(side=tk.RIGHT)

        row2 = tk.Frame(card, bg=C_PANEL)
        row2.pack(fill=tk.X, pady=4)
        tk.Label(row2, text="模糊音", font=("Microsoft YaHei UI", 10),
                 fg=C_TEXT, bg=C_PANEL, width=10, anchor="w").pack(side=tk.LEFT)
        self.fuzzy_var = tk.BooleanVar(value=False)
        tk.Checkbutton(row2, text="z/zh · c/ch · s/sh · n/l · 前后鼻音",
                       variable=self.fuzzy_var, command=self._on_fuzzy,
                       bg=C_PANEL, font=("Microsoft YaHei UI", 9)).pack(side=tk.LEFT)

        row3 = tk.Frame(card, bg=C_PANEL)
        row3.pack(fill=tk.X, pady=4)
        tk.Label(row3, text="上屏方式", font=("Microsoft YaHei UI", 10),
                 fg=C_TEXT, bg=C_PANEL, width=10, anchor="w").pack(side=tk.LEFT)
        self.commit_var = tk.StringVar(value="剪贴板粘贴")
        cb2 = ttk.Combobox(row3, textvariable=self.commit_var, state="readonly",
                           values=["剪贴板粘贴", "Unicode 直输"], width=12)
        cb2.pack(side=tk.LEFT)
        cb2.bind("<<ComboboxSelected>>", self._on_commit_mode)

        # 独立输入区
        card2 = tk.Frame(r, bg=C_PANEL, padx=16, pady=12)
        card2.pack(fill=tk.BOTH, expand=True, padx=12, pady=6)
        tk.Label(card2, text="面板内输入（无需全局钩子也可体验）",
                 font=("Microsoft YaHei UI", 10), fg=C_TEXT, bg=C_PANEL).pack(anchor="w")

        self.entry_var = tk.StringVar()
        self.entry = tk.Entry(card2, textvariable=self.entry_var,
                              font=("Consolas", 12), relief=tk.FLAT, bg="#F2F4F8")
        self.entry.pack(fill=tk.X, pady=8, ipady=6)
        self.entry.bind("<KeyRelease>", self._on_entry_key)

        self.panel_cands = tk.Frame(card2, bg=C_PANEL)
        self.panel_cands.pack(fill=tk.X)
        self.panel_buttons: list[tk.Button] = []
        for i in range(5):
            b = tk.Button(self.panel_cands, text="", relief=tk.FLAT, bg=C_HL,
                          font=("Microsoft YaHei UI", 11), width=8,
                          command=lambda idx=i: self.pick_candidate(idx, panel=True))
            b.pack(side=tk.LEFT, padx=3, pady=3)
            self.panel_buttons.append(b)

        self.suggest_frame = tk.Frame(card2, bg=C_PANEL)
        self.suggest_frame.pack(fill=tk.X, pady=(10, 0))
        self.suggest_label = tk.Label(self.suggest_frame, text="",
                                      font=("Microsoft YaHei UI", 9), fg=C_SUB,
                                      bg=C_PANEL, anchor="w", justify=tk.LEFT, wraplength=360)
        self.suggest_label.pack(anchor="w")

        # 底部按钮
        bottom = tk.Frame(r, bg=C_BG)
        bottom.pack(fill=tk.X, padx=12, pady=(2, 12))
        tk.Button(bottom, text="打开手写板", command=self.open_pad, relief=tk.FLAT,
                  bg=C_ACCENT, fg="#FFFFFF", font=("Microsoft YaHei UI", 10),
                  padx=14, pady=6).pack(side=tk.LEFT, padx=4)
        tk.Button(bottom, text="模拟上屏测试", command=self.test_commit, relief=tk.FLAT,
                  bg=C_PANEL, font=("Microsoft YaHei UI", 10), padx=14, pady=6).pack(side=tk.LEFT, padx=4)
        tk.Button(bottom, text="退出", command=self.on_quit, relief=tk.FLAT,
                  bg=C_PANEL, font=("Microsoft YaHei UI", 10), padx=14, pady=6).pack(side=tk.RIGHT, padx=4)

        self.status = tk.Label(r, text="就绪", font=("Microsoft YaHei UI", 9),
                               fg=C_SUB, bg=C_BG, anchor="w")
        self.status.pack(fill=tk.X, padx=16, pady=(0, 8))

    # ---------------- 事件
    def _hook_key(self, vk: int, down: bool) -> bool:
        swallow, events = self.proc.on_key(vk, down)
        for ev in events:
            self.events.put(ev)
        return swallow

    def _poll_events(self) -> None:
        try:
            while True:
                ev = self.events.get_nowait()
                self._handle_event(ev)
        except queue.Empty:
            pass
        self.root.after(50, self._poll_events)

    def _handle_event(self, ev: tuple) -> None:
        kind = ev[0]
        if kind == "update":
            self._refresh_bar()
        elif kind == "commit":
            self.set_status(f"已上屏：{ev[1]}")
            self.bar.hide()
            self._refresh_panel("")
            self._show_suggest(ev[1])
        elif kind == "toggle":
            self.mode_btn.configure(text=("中" if ev[1] == "CN" else "EN"),
                                    bg=C_ACCENT if ev[1] == "CN" else C_SUB)
            self.set_status(f"切换到{ev[1]}模式")

    def _refresh_bar(self) -> None:
        st = self.state
        if not st.raw:
            self.bar.hide()
            return
        r = self.engine.query(st.raw, page=st.page)
        seg = self.engine.segment_display(st.raw)
        self.bar.show_for(st.raw, seg, r.candidates, r.page, r.total)

    def _refresh_panel(self, raw: str) -> None:
        for i, b in enumerate(self.panel_buttons):
            b.configure(text="")
        if not raw:
            return
        r = self.engine.query(raw, page=0)
        for i, b in enumerate(self.panel_buttons):
            if i < len(r.candidates):
                b.configure(text=r.candidates[i])

    def _show_suggest(self, text: str) -> None:
        words = self.predictor.predict(text, n=8)
        self.suggest_label.configure(text=("联想：" + "  ".join(words)) if words else "")

    def pick_candidate(self, idx: int, panel: bool = False) -> None:
        st = self.state
        if panel:
            raw = self.entry_var.get()
            if not raw:
                return
            r = self.engine.query(raw, page=0)
            if idx < len(r.candidates):
                text = r.candidates[idx]
                self.entry_var.set("")
                self._refresh_panel("")
                self.committer.commit(text)
                self.set_status(f"已上屏：{text}")
                self._show_suggest(text)
            return
        if 0 <= idx < len(st.candidates):
            text = st.candidates[idx]
            self.committer.commit(text)
            st.raw = ""
            st.candidates = []
            st.page = 0
            self.bar.hide()
            self.set_status(f"已上屏：{text}")
            self._show_suggest(text)

    # ---------------- 面板输入
    def _on_entry_key(self, _event=None) -> None:
        raw = self.entry_var.get()
        self._refresh_panel(raw)

    def _on_scheme(self, _event=None) -> None:
        label = self.scheme_var.get()
        for key, name in SCHEME_LABELS:
            if name == label:
                self.engine.scheme = key
                self.set_status(f"输入方案：{name}")
                break

    def _on_fuzzy(self) -> None:
        self.engine.fuzzy = self.fuzzy_var.get()
        self.set_status("模糊音已" + ("开启" if self.engine.fuzzy else "关闭"))

    def _on_commit_mode(self, _event=None) -> None:
        self.committer.mode = "unicode" if self.commit_var.get() == "Unicode 直输" else "paste"
        self.set_status(f"上屏方式：{self.commit_var.get()}")

    def toggle_mode(self) -> None:
        self.state.mode = "EN" if self.state.mode == "CN" else "CN"
        self.mode_btn.configure(text=("中" if self.state.mode == "CN" else "EN"),
                                bg=C_ACCENT if self.state.mode == "CN" else C_SUB)

    def open_pad(self) -> None:
        if self.pad is None or not self.pad.winfo_exists():
            self.pad = HandwritingPad(self.root, self.recognizer, self.committer, self.set_status)
        self.pad.lift()

    def test_commit(self) -> None:
        self.committer.commit("LiteIME 测试")
        self.set_status("已向当前焦点窗口上屏测试文本（请先点开记事本）")

    def set_status(self, text: str) -> None:
        self.status.configure(text=text)

    def on_quit(self) -> None:
        try:
            self.hook.stop()
        except Exception:  # noqa: BLE001
            pass
        self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()


def main() -> None:
    app = App()
    app.run()


if __name__ == "__main__":
    main()
