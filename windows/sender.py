#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Windows 上屏：剪贴板 + 模拟粘贴 / Unicode 直输。"""
from __future__ import annotations

import ctypes
import time
from ctypes import wintypes

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

CF_UNICODETEXT = 13
INPUT_KEYBOARD = 1
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
VK_CONTROL = 0x11
VK_V = 0x56

ULONG_PTR = ctypes.c_size_t


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class INPUT(ctypes.Structure):
    class _I(ctypes.Union):
        _fields_ = [("ki", KEYBDINPUT)]

    _anonymous_ = ("i",)
    _fields_ = [("type", wintypes.DWORD), ("i", _I)]


def set_clipboard(text: str) -> bool:
    if not user32.OpenClipboard(None):
        return False
    try:
        user32.EmptyClipboard()
        data = text.encode("utf-16-le") + b"\x00\x00"
        buf = ctypes.create_string_buffer(data)
        handle = kernel32.GlobalAlloc(0x0042, len(data))  # GMEM_MOVEABLE
        if not handle:
            return False
        ptr = kernel32.GlobalLock(handle)
        ctypes.memmove(ptr, buf, len(data))
        kernel32.GlobalUnlock(handle)
        if not user32.SetClipboardData(CF_UNICODETEXT, handle):
            return False
        return True
    finally:
        user32.CloseClipboard()


def get_clipboard() -> str:
    if not user32.OpenClipboard(None):
        return ""
    try:
        handle = user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return ""
        ptr = kernel32.GlobalLock(handle)
        try:
            raw = ctypes.wstring_at(ptr)
        finally:
            kernel32.GlobalUnlock(handle)
        return raw
    finally:
        user32.CloseClipboard()


def _send_vk(vk: int, up: bool = False) -> None:
    inp = INPUT(type=INPUT_KEYBOARD)
    inp.ki = KEYBDINPUT(vk, 0, KEYEVENTF_KEYUP if up else 0, 0, 0)
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))


def paste() -> None:
    """模拟 Ctrl+V 粘贴。"""
    _send_vk(VK_CONTROL, up=False)
    _send_vk(VK_V, up=False)
    time.sleep(0.015)
    _send_vk(VK_V, up=True)
    _send_vk(VK_CONTROL, up=True)


def type_unicode(text: str) -> None:
    """Unicode 直输（不走剪贴板）。"""
    for ch in text:
        inp = INPUT(type=INPUT_KEYBOARD)
        inp.ki = KEYBDINPUT(0, ord(ch), KEYEVENTF_UNICODE, 0, 0)
        user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))
        inp2 = INPUT(type=INPUT_KEYBOARD)
        inp2.ki = KEYBDINPUT(0, ord(ch), KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0, 0)
        user32.SendInput(1, ctypes.byref(inp2), ctypes.sizeof(inp2))
        time.sleep(0.002)


class Committer:
    """上屏器：默认 剪贴板+粘贴；mode='unicode' 时用 Unicode 直输。"""

    def __init__(self, mode: str = "paste") -> None:
        self.mode = mode

    def commit(self, text: str) -> bool:
        if not text:
            return False
        if self.mode == "unicode":
            type_unicode(text)
            return True
        saved = None
        try:
            saved = get_clipboard()
        except Exception:  # noqa: BLE001
            saved = None
        if not set_clipboard(text):
            type_unicode(text)
            return True
        paste()
        # 恢复原剪贴板（延迟，避免抢在粘贴前）
        if saved:
            def _restore() -> None:
                time.sleep(0.35)
                try:
                    set_clipboard(saved)
                except Exception:  # noqa: BLE001
                    pass
            import threading
            threading.Thread(target=_restore, daemon=True).start()
        return True
