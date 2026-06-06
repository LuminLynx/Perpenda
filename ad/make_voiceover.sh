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

# Synthesize one line -> vo_$1.wav (48k stereo).
#   $1 id   $2 length-scale (>1 = slower/clearer)   $3 text
# The line is trimmed of leading/trailing silence so the adelay below places
# the *speech onset* exactly on the scene beat, then given short edge fades so
# concatenated clips don't introduce click/pop transients (the "weird noise").
say () {
  echo "$3" | piper -m "$M.onnx" --length-scale "$2" -f "raw_$1.wav" 2>/dev/null
  # Trailing trim is gentle (-55 dB, keep 0.12 s) so final plosives like the
  # /t/ release in "bite" survive instead of being clipped.
  "$FF" -y -i "raw_$1.wav" -af \
    "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.02,\
areverse,silenceremove=start_periods=1:start_threshold=-55dB:start_silence=0.12,\
afade=t=in:d=0.02,areverse,afade=t=in:d=0.015,aresample=48000" \
    -ac 2 "vo_$1.wav" 2>/dev/null
}

# Line 3 is split into its three on-screen beats (7.9 / 9.0 / 10.1) so each
# phrase lands on its caption instead of being spoken as one early blob.
# A handful of words are respelled so the espeak phonemizer doesn't emit the
# 'ʲ' palatalization phoneme, which the ryan-high model renders as noise:
#  - "The AI" -> "Thee, AI": the comma drops the ʲ glide and keeps a clear
#    voiced "thee".
#  - "Criterion" -> "crighteerion": k ɹ aɪ t ˈɪ ɹ i ə n ("cry-TEER-ee-un").
#  - "Decision-grade" -> "Decizhun grade": the hyphen made espeak merge it into
#    "decisiong-rade" (…ʒ ə ŋ ɡ…); the respelling reads as a clean "decision grade".
# Display copy elsewhere stays "The AI…", "Criterion…", "Decision-grade…".
# "Perpenda" and the closing line are slowed for a less rushed read.
say 1   1.4  "Perpenda."
say 2   1.0  "Thee, AI calls are yours now."
say 3a  1.1  "Read the bite."
say 3b  1.0  "Make the call."
say 3c  1.0  "Get calibrated."
say 4   1.2  "Crighteerion by crighteerion."
say 5   1.0  "No vanity score. Just where you actually stand."
say 6   1.1  "Decizhun grade AI fluency. On Google Play."

"$FF" -y -i music.wav \
  -i vo_1.wav -i vo_2.wav -i vo_3a.wav -i vo_3b.wav -i vo_3c.wav -i vo_4.wav -i vo_5.wav -i vo_6.wav \
  -filter_complex \
"[1:a]adelay=800|800[a1];[2:a]adelay=3900|3900[a2];[3:a]adelay=7900|7900[a3];\
[4:a]adelay=9000|9000[a4];[5:a]adelay=10100|10100[a5];[6:a]adelay=12200|12200[a6];\
[7:a]adelay=16500|16500[a7];[8:a]adelay=19800|19800[a8]; \
 [a1][a2][a3][a4][a5][a6][a7][a8]amix=inputs=8:duration=longest:normalize=0[vo]; \
 [vo]highpass=f=95,lowpass=f=9000,volume=1.4[vob]; \
 [0:a]volume=0.40[m]; \
 [m][vob]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.95,loudnorm=I=-13:TP=-1:LRA=11[out]" \
 -map "[out]" -ar 48000 vo_mix.wav

"$FF" -y -i "perpenda-ad-4k-$THEME-HQ.mp4" -i vo_mix.wav -c:v copy -c:a aac -b:a 256k -shortest -movflags +faststart "perpenda-ad-$THEME-voiceover.mp4"
echo "done: perpenda-ad-$THEME-voiceover.mp4"
