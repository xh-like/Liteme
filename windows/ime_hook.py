#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全局低级键盘钩子（ctypes 实现，零第三方依赖）。

安装 WH_KEYBOARD_LL 钩子的专用线程 + GetMessage 消息泵；
回调里同步决策吞键/放行，并通过共享 ImeState 与 tkinter 主线程通信。
"""
from __future__ import annotations

import ctypes
import threading
from ctypes import wintypes

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

WH_KEYBOARD_LL = 13
WM_KEYDOWN = 0x0100
WM_KEYUP = 0x0101
WM_SYSKEYDOWN = 0x0104
WM_SYSKEYUP = 0x0105
WM_QUIT = 0x0012
PM_REMOVE = 0x0001

VK_BACK, VK_RETURN, VK_ESCAPE, VK_SPACE = 0x08, 0x0D, 0x1B, 0x20
VK_SHIFT, VK_CONTROL, VK_MENU = 0x10, 0x11, 0x12
VK_PRIOR, VK_NEXT = 0x21, 0x22   # PageUp / PageDown
VK_OEM_MINUS, VK_OEM_PLUS = 0xBD, 0xBB
LLKHF_INJECTED = 0x10

ULONG_PTR = ctypes.c_size_t


class KBDLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [
        ("vkCode", wintypes.DWORD),
        ("scanCode", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


LowLevelKeyboardProc = ctypes.WINFUNCTYPE(
    ctypes.c_long, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM
)


class GlobalKeyboardHook:
    """安装/卸载全局键盘钩子。on_key(vk, down) -> True 表示吞掉该键。"""

    def __init__(self, on_key) -> None:
        self.on_key = on_key
        self._hook_id = None
        self._proc = None
        self._thread: threading.Thread | None = None
        self._tid: int | None = None
        self._ready = threading.Event()

    def start(self) -> bool:
        if self._thread and self._thread.is_alive():
            return True
        self._ready.clear()
        self._thread = threading.Thread(target=self._run, name="ime-hook", daemon=True)
        self._thread.start()
        return self._ready.wait(timeout=3.0)

    def stop(self) -> None:
        if self._tid is not None:
            user32.PostThreadMessageW(self._tid, WM_QUIT, 0, 0)
        self._thread = None
        self._tid = None

    # ------------------------------------------------------------ 内部
    def _run(self) -> None:
        self._tid = kernel32.GetCurrentThreadId()
        self._proc = LowLevelKeyboardProc(self._callback)
        self._hook_id = user32.SetWindowsHookExW(
            WH_KEYBOARD_LL, self._proc, kernel32.GetModuleHandleW(None), 0
        )
        self._ready.set()
        if not self._hook_id:
            return
        msg = wintypes.MSG()
        while True:
            r = user32.GetMessageW(ctypes.byref(msg), None, 0, 0)
            if r == 0 or r == -1:
                break
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))
        if self._hook_id:
            user32.UnhookWindowsHookEx(self._hook_id)
            self._hook_id = None

    def _callback(self, n_code: int, w_param: int, l_param: int) -> int:
        try:
            if n_code == 0:
                kb = ctypes.cast(l_param, ctypes.POINTER(KBDLLHOOKSTRUCT)).contents
                # 自己注入的按键不拦截，防止粘贴模拟回环
                if not (kb.flags & LLKHF_INJECTED):
                    down = w_param in (WM_KEYDOWN, WM_SYSKEYDOWN)
                    up = w_param in (WM_KEYUP, WM_SYSKEYUP)
                    if down or up:
                        try:
                            if self.on_key(int(kb.vkCode), down):
                                return 1
                        except Exception:  # noqa: BLE001
                            pass
        except Exception:  # noqa: BLE001
            pass
        return user32.CallNextHookEx(self._hook_id, n_code, w_param, l_param)
