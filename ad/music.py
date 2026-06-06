import numpy as np, wave, struct, os

SR = 44100
DUR = 23.0
N = int(SR * DUR)
t = np.arange(N) / SR

# ---- note frequencies ----
F2,D2 = 87.31,73.42
C3,D3,E3,F3,G3,A3,Bb3,B3 = 130.81,146.83,164.81,174.61,196.00,220.00,233.08,246.94
C4,D4,E4,F4,G4,A4,C5,F5 = 261.63,293.66,329.63,349.23,392.00,440.00,523.25,698.46

# chord voicings (root listed first; a sub octave is added for warmth)
chords = {
 'F':  [F3,A3,C4,E4],
 'Dm': [D3,F3,A3,C4],
 'Bb': [Bb3,D4,F4,A4],
 'C':  [C3,G3,C4,E4],
 'Fr': [F3,A3,C4,F4],
}
# (start, end, chord) — aligned to the scene cuts
segs = [(0.0,4.2,'F'),(4.2,7.8,'Dm'),(7.8,12.0,'Bb'),(12.0,16.3,'C'),(16.3,19.0,'Dm'),(19.0,23.5,'Fr')]

def rcos(x):  # 0..1 raised-cosine
    return 0.5 - 0.5*np.cos(np.pi*np.clip(x,0,1))

def voice(freq, detune):
    f = freq*detune
    w = 2*np.pi*f*t
    return np.sin(w) + 0.18*np.sin(2*w) + 0.05*np.sin(3*w)

padL = np.zeros(N); padR = np.zeros(N)
XF = 0.7  # crossfade seconds
for (s,e,name) in segs:
    notes = chords[name]
    notes = [notes[0]/2*0.0+notes[0]] + notes  # placeholder keep
    env = np.zeros(N)
    a = rcos((t-s)/XF)                 # fade in
    b = 1.0 - rcos((t-(e))/XF)         # fade out after e
    env = np.clip(np.minimum(a,b),0,1)
    env[t< s-0.01]=0
    sub = chords[name][0]/2            # sub-octave root
    for i,nf in enumerate(chords[name]):
        amp = 0.9 if i==0 else 0.7
        padL += env*amp*voice(nf,0.9975)
        padR += env*amp*voice(nf,1.0025)
    padL += env*0.5*voice(sub,0.999)
    padR += env*0.5*voice(sub,1.001)

# gentle breathing
lfo = 1+0.06*np.sin(2*np.pi*0.08*t)
padL*=lfo; padR*=lfo
padL*=0.05; padR*=0.05   # pad sits low

# ---- sparse felt accents (time, freq, amp) ----
acc = [
 (7.9,D4,0.16),(9.0,F4,0.16),(10.1,A4,0.16),      # the three loop lines, rising
 (12.2,C5,0.09),                                   # calibrate lift
 (12.7,G4,0.12),(13.1,C5,0.12),(13.5,E4,0.085),   # ✓ ✓ ✗ (last softer/lower)
 (16.5,A3,0.10),                                   # "no vanity score"
 (19.2,F4,0.14),(20.6,C5,0.12),(21.4,F5,0.07),     # end card resolve
]
accL=np.zeros(N); accR=np.zeros(N)
for (t0,f,amp) in acc:
    idx = t>=t0
    tau=0.9; at=0.006
    loc = t-t0
    env = np.where(idx, np.exp(-loc/tau), 0.0)
    env *= np.clip(loc/at,0,1)        # tiny attack
    w=2*np.pi*f*loc
    tone = np.sin(w)+0.3*np.sin(2*w)+0.12*np.sin(3*w)
    tone = np.where(idx, tone, 0.0)
    accL += amp*env*tone*0.98
    accR += amp*env*tone*1.0

L = padL+accL; R = padR+accR
# normalize
peak = max(np.max(np.abs(L)), np.max(np.abs(R)), 1e-6)
L = L/peak*0.72; R = R/peak*0.72

inter = np.empty(N*2)
inter[0::2]=L; inter[1::2]=R
data = (inter*32767).astype('<i2').tobytes()
_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'music_raw.wav')
with wave.open(_OUT,'wb') as wv:
    wv.setnchannels(2); wv.setsampwidth(2); wv.setframerate(SR); wv.writeframes(data)
print('wrote music_raw.wav', round(len(data)/4/SR,2),'s')
