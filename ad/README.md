# Perpenda video ad — generation toolkit

Self-contained pipeline that renders the Perpenda 16:9 video ad at **4K**, scores it
with an original soundtrack, and (optionally) adds a neural **voiceover**.

The video is a deterministic HTML/canvas animation captured frame-by-frame with
Playwright (so timing is exact), then encoded with ffmpeg. Brand fonts
(Source Serif 4 / JetBrains Mono) and the app icon are bundled here.

## Files
- `ad.html` — the animation. `?theme=light` (cream) or default (dark ink). The
  end card holds, so length is configurable; rendered at 24.5s (the extra time
  lets the slower amy voice read the closing lines unhurried).
- `capture_ad.js` — Playwright capture → PNG frames at 3840×2160 (deterministic clock).
- `music.py` — synthesizes the original ambient soundtrack (`music_raw.wav`).
- `thumb.html` + `rthumb.js` — 4K thumbnails (concepts a/b, dark/light).
- `fonts/`, `icon-512.png` — brand assets used by the animation.
- `make_video.sh` — render + encode + score (light & dark).
- `make_voiceover.sh` — download a Piper voice, generate VO, mix over the music, mux.

## Prerequisites
```
npm install playwright ffmpeg-static
npx playwright install chromium
pip install numpy            # for music.py
```
ffmpeg comes from the `ffmpeg-static` npm package (has libx264). `FF=$(node -e "process.stdout.write(require('ffmpeg-static'))")`.

## Build the video (light, 4K, with soundtrack)
```
bash make_video.sh
```
Produces `perpenda-ad-4k-light-HQ.mp4` (silent), `music.wav`, and
`perpenda-ad-4k-light-music.mp4` (with soundtrack).

## Add a natural voiceover (needs HuggingFace allowlisted)
Allowlist hosts (see `allowlist-urls.txt`): `huggingface.co`,
`cas-bridge.xethub.hf.co`, `cdn-lfs.huggingface.co`. Then:
```
pip install piper-tts
bash make_voiceover.sh ryan-high      # or lessac-medium / amy-medium
```
Produces `perpenda-ad-light-voiceover.mp4`.

## Scene timings (for VO / music sync), seconds
| t | scene |
|---|---|
| 0.7 | brand open ("Perpenda") |
| 3.9 | "The AI calls are yours now." |
| 7.9 / 9.0 / 10.1 | Read the bite / Make the call / Get calibrated |
| 12.2 | "Criterion by criterion." |
| 12.7 / 13.1 / 13.5 | ✓ ✓ ✗ checklist rows |
| 16.5 | "No vanity score. / Just where you actually stand." |
| 19.0 | end card: icon + "Perpenda" + "Decision-grade AI fluency." + "On Google Play" |

Video stream is always copied (`-c:v copy`) when muxing audio — never re-encoded.
