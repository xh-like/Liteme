#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输入状态机：与界面无关的按键处理逻辑（可单测）。

KeyProcessor.on_key(vk, down) -> bool（True=吞掉该键）
返回事件列表由外部消费：[('update',), ('commit', text), ('toggle', mode), ...]
"""
from __future__ import annotations

import threading
from dataclasses import dataclass, field

from core.engine import PinyinEngine

VK_BACK, VK_RETURN, VK_ESCAPE, VK_SPACE = 0x08, 0x0D, 0x1B, 0x20
VK_SHIFT, VK_CONTROL, VK_MENU = 0x10, 0x11, 0x12
VK_PRIOR, VK_NEXT = 0x21, 0x22
VK_OEM_MINUS, VK_OEM_PLUS = 0xBD, 0xBB


@dataclass
class ImeState:
    mode: str = "CN"                  # CN / EN
    raw: str = ""                     # 正在输入的编码
    candidates: list[str] = field(default_factory=list)
    cand_raw: str = ""                # candidates 对应的编码
    page: int = 0
    page_size: int = 9
    suggest: list[str] = field(default_factory=list)   # 联想候选


class KeyProcessor:
    def __init__(self, engine: PinyinEngine, state: ImeState, committer) -> None:
        self.engine = engine
        self.state = state
        self.committer = committer
        self.lock = threading.Lock()
        self._shift_down_at: float | None = None
        self._shift_solo = True
        self._other_key_since_shift = False

    # ------------------------------------------------------------ 候选
    def refresh(self) -> None:
        st = self.state
        if not st.raw:
            st.candidates = []
            st.cand_raw = ""
            st.page = 0
            return
        r = self.engine.query(st.raw, page=st.page)
        st.candidates = r.candidates
        st.cand_raw = st.raw
        st.page = r.page

    def _ensure_candidates(self) -> None:
        if self.state.cand_raw != self.state.raw:
            self.state.page = 0
            self.refresh()

    # ------------------------------------------------------------ 提交
    def _commit_text(self, text: str, events: list) -> None:
        st = self.state
        st.raw = ""
        st.candidates = []
        st.cand_raw = ""
        st.page = 0
        self.committer.commit(text)
        events.append(("commit", text))

    def select(self, index: int, events: list) -> None:
        self._ensure_candidates()
        st = self.state
        if 0 <= index < len(st.candidates):
            self._commit_text(st.candidates[index], events)
        elif st.raw:
            # 越界则上屏原始编码
            self._commit_text(st.raw, events)

    # ------------------------------------------------------------ 按键
    def on_key(self, vk: int, down: bool) -> tuple[bool, list]:
        """返回 (是否吞键, 事件列表)。"""
        with self.lock:
            return self._on_key_locked(vk, down)

    def _on_key_locked(self, vk: int, down: bool) -> tuple[bool, list]:
        st = self.state
        events: list = []

        # Shift 单击切换中/英
        if vk == VK_SHIFT:
            if down:
                self._shift_down_at = vk
                self._other_key_since_shift = False
            else:
                if not self._other_key_since_shift:
                    st.mode = "EN" if st.mode == "CN" else "CN"
                    if st.raw:
                        self._commit_text(st.raw, events)
                    events.append(("toggle", st.mode))
                    return True, events
            return False, events

        if down and vk not in (VK_SHIFT, VK_CONTROL, VK_MENU):
            self._other_key_since_shift = True

        # Ctrl+Space 切换中/英
        if vk == VK_SPACE and down and self._ctrl_pressed():
            st.mode = "EN" if st.mode == "CN" else "CN"
            events.append(("toggle", st.mode))
            return True, events

        # 英文模式：全部放行（Ctrl/Alt 组合也放行）
        if st.mode == "EN" or self._ctrl_pressed() or self._alt_pressed():
            return False, events

        if not down:
            return False, events

        # ---- 字母
        if 0x41 <= vk <= 0x5A:
            st.raw += chr(vk + 32)
            st.page = 0
            self.refresh()
            events.append(("update",))
            return True, events

        # ---- 数字选字（有编码时）
        if 0x30 <= vk <= 0x39 and st.raw:
            digit = vk - 0x30
            self.select(9 if digit == 0 else digit - 1, events)
            events.append(("update",))
            return True, events

        # ---- 空格：上屏首选
        if vk == VK_SPACE and st.raw:
            self.select(0, events)
            events.append(("update",))
            return True, events

        # ---- 回车：上屏原始编码
        if vk == VK_RETURN and st.raw:
            self._commit_text(st.raw, events)
            events.append(("update",))
            return True, events

        # ---- 退格
        if vk == VK_BACK and st.raw:
            st.raw = st.raw[:-1]
            st.page = 0
            self.refresh()
            events.append(("update",))
            return True, events

        # ---- Esc 清空
        if vk == VK_ESCAPE and st.raw:
            st.raw = ""
            st.page = 0
            self.refresh()
            events.append(("update",))
            return True, events

        # ---- 翻页
        if st.raw and vk in (VK_PRIOR, VK_OEM_MINUS):
            st.page = max(0, st.page - 1)
            self.refresh()
            events.append(("update",))
            return True, events
        if st.raw and vk in (VK_NEXT, VK_OEM_PLUS):
            st.page += 1
            self.refresh()
            if not st.candidates:   # 越界回退
                st.page -= 1
                self.refresh()
            events.append(("update",))
            return True, events

        return False, events

    def _ctrl_pressed(self) -> bool:
        return bool(ctypes_get_key_state(VK_CONTROL))

    def _alt_pressed(self) -> bool:
        return bool(ctypes_get_key_state(VK_MENU))


def ctypes_get_key_state(vk: int) -> int:
    import ctypes
    return ctypes.WinDLL("user32").GetAsyncKeyState(vk) & 0x8000
