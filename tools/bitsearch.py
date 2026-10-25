#!/usr/bin/env python3
# hunts for the "weekly timer enabled" flag in the undecoded bytes of the hitachi keypress frame.
# the unit keeps its stored schedule and lights an indicator while it's enabled. our plain frame
# turns the light off, a frame with the right bit set turns it back on, so the unit grades each try.
# bisection over the candidate bits, you answer what the unit did after each send.
# usage: tools/bitsearch.py [byte ...]   default bytes 18-26. needs the app installed and adb connected.
import subprocess, sys, time

BYTES = range(18, 27)
bits = [(b, i) for b in (list(map(int, sys.argv[1:])) or BYTES) for i in range(8)]

def send(bits):
    mask = {}
    for b, i in bits:
        mask[b] = mask.get(b, 0) | 1 << i
    arg = ",".join(f"{b}:{v:02x}" for b, v in sorted(mask.items()))
    subprocess.run(["adb", "shell", "am", "broadcast", "-n", "remy.irremote/.DebugReceiver", "--es", "or", arg],
                   check=True, capture_output=True)
    time.sleep(1.5)
    return arg

def ask(bits):
    print(f"sent {send(bits) or 'plain frame'} ({len(bits)} bits)")
    while True:
        a = input("  schedule light on? y / n / x = unit ignored it (no beep): ").strip().lower()
        if a in ("y", "n", "x"):
            if a == "y":
                send([])  # back to light off before the next try
            return a

def search(bits):
    if len(bits) == 1:
        return bits[0] if ask(bits) == "y" else None
    if ask(bits) == "n":
        return None
    h = len(bits) // 2  # y: in here somewhere, x: unknown, split either way
    return search(bits[:h]) or search(bits[h:])

print("unit on, schedule light off (press ON in the app if needed), phone pointed at it")
hit = search(bits)
print(f"found: byte {hit[0]} bit {hit[1]} (0x{1 << hit[1]:02x})" if hit else "nothing in those bytes")
