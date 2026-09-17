#!/usr/bin/env python3
"""Send real input to the smoke test's private Xvfb server; uses system X11/XTest libraries."""
import ctypes as c
import os
import sys
import time


def main():
    if not os.environ.get("DISPLAY") or not os.environ.get("XAUTHORITY"):
        raise SystemExit("Run the client smoke test with xvfb-run and its private display authority")
    key, button, modifier = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    x11, xtst = c.CDLL("libX11.so.6"), c.CDLL("libXtst.so.6")
    x11.XOpenDisplay.argtypes, x11.XOpenDisplay.restype = [c.c_char_p], c.c_void_p
    x11.XStringToKeysym.argtypes, x11.XStringToKeysym.restype = [c.c_char_p], c.c_ulong
    x11.XKeysymToKeycode.argtypes, x11.XKeysymToKeycode.restype = [c.c_void_p, c.c_ulong], c.c_uint
    x11.XFlush.argtypes = x11.XCloseDisplay.argtypes = [c.c_void_p]
    xtst.XTestFakeKeyEvent.argtypes = xtst.XTestFakeButtonEvent.argtypes = [c.c_void_p, c.c_uint, c.c_int, c.c_ulong]
    display = x11.XOpenDisplay(None)
    if not display:
        raise SystemExit("Cannot open smoke test display")

    def keycode(name):
        return x11.XKeysymToKeycode(display, x11.XStringToKeysym(name.encode())) if name else 0

    key_code, modifier_code = keycode(key), keycode(modifier)

    def send(code, down, mouse=False):
        if code:
            (xtst.XTestFakeButtonEvent if mouse else xtst.XTestFakeKeyEvent)(display, code, down, 0)
            x11.XFlush(display)

    try:
        time.sleep(0.2)
        if modifier_code:
            send(modifier_code, True)
            time.sleep(0.15)
        send(key_code or button, True, not key_code)
        time.sleep(0.12)
        send(key_code or button, False, not key_code)
        time.sleep(0.15)
    finally:
        send(key_code or button, False, not key_code)
        send(modifier_code, False)
        x11.XCloseDisplay(display)


if __name__ == "__main__":
    main()
