#!/usr/bin/env python3
# decodes a capture of the hitachi remote into frame bytes. on the pi: ir-ctl -d /dev/lirc0 -r > cap.txt
# (gpio-ir overlay on GPIO18, remote about 1 m from the sensor or long bursts saturate it).
# usage: tools/decode.py cap.txt
import sys, re
for path in sys.argv[1:]:
    toks = re.findall(r"([+-])(\d+)", open(path).read())
    frames, cur, i = [], None, 0
    while i < len(toks) - 1:
        s, v = toks[i][0], int(toks[i][1])
        if s == "+" and v > 2500 and toks[i+1][0] == "-" and 1200 < int(toks[i+1][1]) < 2200:
            cur = []; frames.append(cur); i += 2; continue
        if cur is not None and s == "+" and toks[i+1][0] == "-":
            sp = int(toks[i+1][1])
            if sp > 3000: cur = None
            else: cur.append(1 if sp > 900 else 0)
            i += 2; continue
        i += 1
    for n, bits in enumerate(frames):
        by = [sum(b << k for k, b in enumerate(bits[j:j+8])) for j in range(0, len(bits) - 7, 8)]
        print(f"{path} frame {n}: {len(bits)} bits, {len(bits)//8} bytes")
        print("  " + " ".join(f"{b:02x}" for b in by))
