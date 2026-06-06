#!/usr/bin/env bash
# Add a natural Piper voiceover over the soundtrack, timed to the scenes.
# Run AFTER make_video.sh (needs music.wav + perpenda-ad-4k-<theme>-HQ.mp4).
# Requires HuggingFace allowlisted + `pip install piper-tts`.
# Usage: bash make_voiceover.sh [ryan-high|lessac-medium|amy-medium] [light|dark]
set -e
cd "$(dirname "$0")"
FF=$(node -e "process.stdout.write(require('ffmpeg-static'))")
VOICE="${1:-ryan-high}"; THEME="${2:-light}"
case "$VOICE" in
  ryan-high)     P="en/en_US/ryan/high/en_US-ryan-high";;
  lessac-medium) P="en/en_US/lessac/medium/en_US-lessac-medium";;
  amy-medium)    P="en/en_US/amy/medium/en_US-amy-medium";;
  *) echo "unknown voice $VOICE"; exit 1;;
esac
BASE="https://huggingface.co/rhasspy/piper-voices/resolve/main/$P"
M="models/$(basename "$P")"
mkdir -p models
[ -f "$M.onnx" ]      || curl -sSL -o "$M.onnx"      "$BASE.onnx"
[ -f "$M.onnx.json" ] || curl -sSL -o "$M.onnx.json" "$BASE.onnx.json"

say () { echo "$3" | piper -m "$M.onnx" -f "raw_$1.wav"; "$FF" -y -i "raw_$1.wav" -ac 2 -ar 48000 "vo_$1.wav" 2>/dev/null; }
say 1 0 "Perpenda."
say 2 0 "The AI calls are yours now."
say 3 0 "Read the bite. Make the call. Get calibrated."
say 4 0 "Criterion by criterion."
say 5 0 "No vanity score. Just where you actually stand."
say 6 0 "Decision-grade AI fluency. On Google Play."

"$FF" -y -i music.wav -i vo_1.wav -i vo_2.wav -i vo_3.wav -i vo_4.wav -i vo_5.wav -i vo_6.wav -filter_complex \
"[1:a]adelay=800|800[a1];[2:a]adelay=3900|3900[a2];[3:a]adelay=7900|7900[a3];[4:a]adelay=12300|12300[a4];[5:a]adelay=16400|16400[a5];[6:a]adelay=19300|19300[a6]; \
 [a1][a2][a3][a4][a5][a6]amix=inputs=6:duration=longest:normalize=0[vo]; \
 [vo]highpass=f=95,lowpass=f=9000,volume=1.4[vob]; \
 [0:a]volume=0.40[m]; \
 [m][vob]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.95,loudnorm=I=-13:TP=-1:LRA=11[out]" \
 -map "[out]" -ar 48000 vo_mix.wav

"$FF" -y -i "perpenda-ad-4k-$THEME-HQ.mp4" -i vo_mix.wav -c:v copy -c:a aac -b:a 256k -shortest -movflags +faststart "perpenda-ad-$THEME-voiceover.mp4"
echo "done: perpenda-ad-$THEME-voiceover.mp4"
