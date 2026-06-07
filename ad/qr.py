#!/usr/bin/env python3
"""Generate an on-brand Perpenda QR code with the app icon in the centre.

Usage: python3 qr.py "<url>" <out.png> [theme] [icon]
  theme: light (cream, default) | dark (ink) | paper (near-white) | plain (b/w)
  icon:  1 to embed icon-512.png in the centre (default), 0 for none
High error-correction (H) keeps it scannable even with the centre logo.
"""
import sys, segno
from PIL import Image, ImageDraw

url   = sys.argv[1]
out   = sys.argv[2] if len(sys.argv) > 2 else "qr.png"
theme = sys.argv[3] if len(sys.argv) > 3 else "light"
icon  = (sys.argv[4] if len(sys.argv) > 4 else "1") == "1"

PALETTE = {
    "light": ("#23201C", "#FAF3DC"),  # ink on cream
    "paper": ("#23201C", "#FBF9F3"),  # ink on near-white
    "dark":  ("#F3EFE6", "#1C1815"),  # paper on ink
    "plain": ("#000000", "#FFFFFF"),
}
dark, light = PALETTE.get(theme, PALETTE["light"])

qr = segno.make(url, error="h")
SCALE, BORDER = 24, 4
qr.save(out, scale=SCALE, border=BORDER, dark=dark, light=light)

if icon:
    img = Image.open(out).convert("RGBA")
    W, H = img.size
    logo = Image.open("icon-512.png").convert("RGBA")
    # keep the logo small (~20%) so error-correction can still recover the code
    side = int(W * 0.20)
    logo = logo.resize((side, side), Image.LANCZOS)
    # rounded-rect plate behind the logo in the background colour, for contrast
    pad = int(side * 0.14)
    plate = Image.new("RGBA", (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(plate)
    r = int((side + 2 * pad) * 0.22)
    d.rounded_rectangle([0, 0, side + 2 * pad - 1, side + 2 * pad - 1], radius=r, fill=light)
    px, py = (W - plate.width) // 2, (H - plate.height) // 2
    img.alpha_composite(plate, (px, py))
    img.alpha_composite(logo, (px + pad, py + pad))
    img.convert("RGB").save(out)

print("wrote", out)
