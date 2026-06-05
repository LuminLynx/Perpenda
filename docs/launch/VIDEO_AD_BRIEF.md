# Perpenda — Video Ad Brief

Brief for Claude Design (or any video tool) to produce a brand-aligned promo
video for Perpenda. Pull all assets from this repo. **Voice: calm, confident,
anti-hype.** No neon, cyberpunk, sci-fi, "AI brain" imagery, or hype music —
the restraint *is* the brand.

## Concept

**"The loop."** Tell the product's core loop as the story — Read → Decide →
Calibrate → Return — in the editorial ink/paper/oxblood style. Quiet
confidence, not spectacle. The video should feel like the same world as
perpenda.com.

## Formats

- **Primary:** 16:9, ~22s. Pad the end card to ~30s so it can double as the
  **Google Play promo video** (Play requires a YouTube URL, 30s–2min, landscape).
- **Social cut:** 9:16 (and/or 1:1), ~15s, tighter pacing.
- Export with **no transparency**; standard MP4/H.264.

## Visual style

- Motion: slow fades, hairlines drawing in. No fast cuts, no zoom-bursts.
- Type: serif display (match the site). Hairline rules. Generous margins.
- Legible at small sizes (it plays as a thumbnail too).
- Sound: minimal — soft ambient pad or a single piano note per beat. **No**
  epic/hype track. Voiceover optional; if omitted, let the type carry it.

## Palette (use the repo tokens, not approximations)

Source of truth: `docs/styles/colors_and_type.css`

- Ink (background): `#221E1B`
- Paper (text / marks): `#F3EFE6`
- Oxblood (accent only — hairlines, the level line): `#7D2A1A`

## Assets (from this repo — use the dark variants to match the ink background)

Real app screens (use these for the mock frames — **do not invent UI**):

- Unit reader: `docs/app-store/screens-dark/02-unit-reader.png`
- Path home: `docs/app-store/screens-dark/01-path-home.png`
- (light variants if needed: `docs/app-store/screens/02-unit-reader.png`,
  `docs/app-store/screens/01-path-home.png`)

Brand mark (the serif "P" with a level / plumb-line struck through it):

- `docs/brand/perpenda-mark-dark.svg` (also `perpenda-mark.svg`,
  `perpenda-mark-ink.svg`)

Wordmark:

- `docs/brand/perpenda-wordmark-dark.svg` (light: `perpenda-wordmark.svg`)

## Storyboard (22s)

| Time | Visual | On-screen text |
|------|--------|----------------|
| 0–3s | Ink screen; a thin oxblood hairline draws across | **Your team is making AI calls every week.** |
| 3–6s | Text fades; new line | **They're looking to you to know which ones to trust.** |
| 6–9s | `02-unit-reader` screen, the bite text in focus | *Read the bite.* |
| 9–12s | `02-unit-reader`, decision-prompt field with a cursor | *Make the call — in your own words.* |
| 12–16s | Per-criterion grade: small ✓/✗ marks + a confidence note | *Get calibrated. Criterion by criterion. No vanity score.* |
| 16–19s | `01-path-home` with a "Reviews due" pill resurfacing | *Then it comes back, so the judgment sticks.* |
| 19–22s | End card: P-with-level mark + wordmark on ink, oxblood hairline | **Perpenda** · *Decision-grade AI fluency.* |
| 22–30s | Hold the end card, slow fade; a small line appears | *On Google Play.* |

## Voiceover (optional — calm, unhurried)

> Your team's making decisions with AI in the room — and looking to you to call
> which ones to trust. Perpenda trains that judgment. Read the trade-off. Make
> the call. Get graded, criterion by criterion. Then it comes back, until it
> sticks. Perpenda. Decision-grade AI fluency.

## End card (always)

P-with-level mark + "Perpenda" wordmark + tagline *"Decision-grade AI fluency."*
on ink, with a single oxblood hairline. Full visual coherence with the website,
app icon, and Play feature graphic.

## Taglines (interchangeable)

- Decision-grade AI fluency.
- LLM systems for PMs — one trade-off at a time.
