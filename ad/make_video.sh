#!/usr/bin/env bash
# Render + encode the Perpenda ad at 4K and add the original soundtrack.
# Run from the ad/ directory. Requires: npm i playwright ffmpeg-static; npx playwright install chromium; pip install numpy
set -e
cd "$(dirname "$0")"
FF=$(node -e "process.stdout.write(require('ffmpeg-static'))")
THEME="${1:-light}"   # light | dark
AD="$PWD/ad.html"; [ "$THEME" = "light" ] && AD="$AD?theme=light"

echo "== rendering frames ($THEME) =="
AD="$AD" OUT=frames FPS=30 DURATION=23 node capture_ad.js

echo "== encoding 4K master =="
"$FF" -y -framerate 30 -i frames/f_%05d.png -r 30 \
  -vf "deband=1thr=0.02:2thr=0.02:3thr=0.02:range=16,format=yuv420p" \
  -c:v libx264 -b:v 40M -maxrate 45M -bufsize 90M -preset slow -x264-params "aq-mode=3" \
  -movflags +faststart "perpenda-ad-4k-$THEME-HQ.mp4"

echo "== soundtrack =="
python3 music.py
"$FF" -y -i music_raw.wav -af "highpass=f=45,lowpass=f=6500,aecho=0.85:0.9:55|110:0.22|0.13,afade=t=in:st=0:d=0.8,afade=t=out:st=21.3:d=1.7,loudnorm=I=-15:TP=-1.5:LRA=11" -ar 48000 music.wav

echo "== mux soundtrack =="
"$FF" -y -i "perpenda-ad-4k-$THEME-HQ.mp4" -i music.wav -c:v copy -c:a aac -b:a 256k -shortest -movflags +faststart "perpenda-ad-4k-$THEME-music.mp4"
echo "done: perpenda-ad-4k-$THEME-music.mp4"
