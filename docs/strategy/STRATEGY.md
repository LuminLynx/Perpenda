# Perpenda — Product Strategy

**Name:** Perpenda *(originally codenamed Libella)*
**Status:** Locked top-down strategy (recorded 2026-05-04)
**Supersedes:** Any earlier strategic framing in this repo. The bottom-up tactical breakdown in `ROADMAP.md` complements but does not override this document.

This document is the canonical source of truth for product strategy. It was produced through a top-down debate that locked decisions in order: Audience → Problem → Vision → Value Proposition → Product Principles → Core Experience/Loop → Features → Architecture, plus the cross-cutting risks (T1, T2) and Naming. Each section was stress-tested before locking.

---

## 1. Vision

> *Perpenda makes product professionals AI-fluent enough to lead the decisions their teams now have to make.*

The vision is one breath. It names the audience, the change, and quietly carries the urgency from the Problem.

---

## 2. Audience

**Who.** Product-side professionals (PM, PMM, founder, BD, design lead, exec) with **stakes** in being AI-literate — a career switch, a current role, or an active build decision.

**Destination.** AI-fluent **decider**. Can confidently make build/buy/skip decisions on AI features, talk shop with engineers credibly, and recognize trade-offs and failure modes.

**Wedge (the combination none of the alternatives nail).**

- (a) Mobile-first
- (b) Bite-sized
- (e1) **Trade-off-first pedagogy** — *primary differentiator*
- (e2) **Calibrated, sourced reliability** — *credibility moat that makes (e1) defensible*

The audience is *not* AI-curious mobile browsers. The product is built for reliable, consistent learning to the highest possible standard, not engagement-bait.

---

## 3. Problem

Product professionals are now expected to make AI-shaping decisions every week, but the available learning resources force a builder's curriculum (math, code, completion-graded) or hand them scattered hot takes. **There is no curricular path that takes a non-builder from AI-curious to decision-grade** — to where they can reason about trade-offs, recognize failure modes, and decide what to ship and why. Models change weekly, the cost of getting it wrong climbs, and "exposure" is no longer enough.

Two registers, both true, used in different contexts:

- **Internal (harsh):** PMs accumulate exposure but not durable competence. They recognize terms but can't reason about trade-offs.
- **External (kind):** *"I keep reading about AI but it's not sticking — I want to actually understand this, not just nod along."*

---

## 4. Value Proposition

> **For** product professionals with stakes in AI-shaping decisions
> **Who** need to be fluent enough to decide, not fluent enough to build
> **Perpenda** is a **mobile-native learning path**
> **That** turns scattered AI exposure into **decision-grade competence**
> **Unlike** engineer-flavored courses, scattered newsletters, or AI chatbots used as tutors
> **It** teaches every concept through its trade-offs, with every claim sourced and calibrated

Alternatives are named generically here. Brand names belong in positioning materials, not in the canonical value prop.

---

## 5. Product Principles

A real principle excludes things. If it doesn't rule something out, it isn't a principle.

### P1. Decisions before mechanism. *(primary)*

Every concept teaches *what to do with this* before *how it works*. Mechanism is available, but never the gate.

**Excludes:** math-first explanations, code-first explanations, anything that requires linear algebra to reach the trade-off.

### P2. Calibrate, don't bluff. *(primary)*

Every claim is sourced (primary preferred), tagged for confidence (settled / contested / unsettled). *"We don't know yet"* is published as a valid answer.

**Excludes:** vibes, opinion-as-fact, hand-wave, undated claims, false certainty.

### P3. Path, not catalog.

The home is "continue your path," not "browse the glossary." Reference exists, but as a byproduct of the curriculum.

**Excludes:** glossary-first home screens, search-as-primary navigation, à-la-carte content discovery.

### P4. Bite first, depth on tap.

Every unit has a 90-second take. Going deeper is one tap away, not a different product.

**Excludes:** long-form-only content, shallow-only content, "see our blog post for more."

### P5. Quality ceiling, not content scale.

Better to ship 50 perfect units than 500 mediocre ones. Scale follows quality, never replaces it.

**Excludes:** AI-generated mass content, race-to-1000-terms, lowering the editorial bar to hit a number.

**Primary pair:** P1 + P2 are the wedge. The other three are supporting and may be cut under pressure; P1 and P2 may not.

---

## 6. Core Experience / Loop

### Definitions

- **Unit** — the smallest thing worth opening the app for. One concept, taught through one trade-off. ~90-second bite + depth on tap.
- **Session** — 1 unit + decision prompt + maybe a review. 5–10 minutes target.
- **Path (v1)** — one canonical track: *"LLM Systems for PMs"*, ~20 units, sequenced with prereqs.

### Six-step session arc

1. **Continue.** Open the app; home is "your next unit." (P3)
2. **Bite.** 90-second take of the new concept, framed by its trade-off. (P1, P4)
3. **Decide.** A decision-flavored prompt — *open-ended*, not factual recall.
4. **Calibrate.** Show what's settled, what's contested, the sources. User sees how their answer maps to consensus. (P2)
5. **Progress.** Mark complete; advance the path; optional 20-minute deep-dive on tap.
6. **Return.** Spaced review of older units shows up the next day, alongside the next new unit.

### Key calls within the loop

- **Decide step (D2).** Open-ended user answer, LLM-graded against an authored rubric, with the rubric and grader confidence visible to the user. Multiple-choice and self-grade are explicitly rejected.
- **Spaced review.** In v1. Without it, the path evaporates.
- **Path count.** One path, well-built. Multiple paths divides editorial attention and contradicts P5.

### Excluded from the loop

- Streaks, daily reminders, leaderboards, social — Duolingo-flavor; doesn't fit α-PM tone.
- Free-text "Ask the Glossary" as a session entry point — conflicts with P3.
- A separate "today's hot topic / news" feed — different product (newsletter, not curriculum).

---

## 7. Features

### Spine — required for the loop to work

| # | Feature | Maps to |
|---|---|---|
| F1 | Path home / "Continue" | Loop step 1, P3 |
| F2 | Unit reader (bite + depth on tap) | Loop step 2, P1, P4 |
| F3 | Decision prompt | Loop step 3, P1 |
| F4 | LLM grader with authored rubric | Loop step 4, P1, P2 |
| F5 | Spaced review scheduler | Loop step 6 |
| F6 | Path overview / "you are here" | Loop step 5, P3 |
| F7 | Account + progress tracking | Persistence |

### Sidewall — useful but cuttable

- **S1 Glossary side-door.** Keep the data; demote the top-level tabs. Glossary entries become accessible *from inside a unit* ("see related"), not from home.
- **S2 Interactive widgets** (Bundle-0-style Try-It, Pitfall demos). **Flagship units only.** Not every unit. Editorial cost is real.
- **S3 Settings.** Keep as-is.

### Cut for v1 (explicit)

- Contribution / "Create Term Draft" flows
- "Ask Glossary" as a front door (could survive as per-unit help inside F2)
- Streaks, leaderboards, social
- Multiple paths
- AI Learning Modules' style picker (Quick recap / Interview prep / Hands-on coding / Conceptual deep-dive)
- "Concept previews" tab

### Unit anatomy (9 slots)

Each unit must have all of:

1. Title
2. Single-sentence definition
3. Trade-off framing (*when this matters / when this breaks / what it costs*)
4. 90-second bite (the read)
5. Depth (longer reader OR interactive widget OR both)
6. Calibration tags on key claims
7. Sources (primary preferred, dated)
8. Decision prompt + authored rubric
9. Prereq pointers (for path scheduler)

Plus, locked under T2:

- Ground-truth answer–grade pairs for the regression set, tiered — flagship units `>= 20`, standard units `>= 10`, **never zero**.

### Authoring tool

Parallel workstream. Not user-facing; founder-facing. CLI / markdown convention / admin form — implementation TBD. Doesn't gate user-facing v1 but does gate content production rate.

---

## 8. Architecture

### Inherits (current stack works)

- Android client (Kotlin + Jetpack Compose)
- FastAPI backend
- PostgreSQL database
- Railway hosting
- JWT auth

### Reshapes

- **Data model.** From term-centric to **path-centric**. New first-class entities: `Unit`, `Path`, `DecisionPrompt`, `Rubric`, `Source`, `CalibrationTag`, `Completion`, `ReviewSchedule`. Terms become a consequence of units, not the spine.
- **Home screen.** From retrieval-shaped (Browse / Categories / Search) to path-shaped ("Continue").

### New infrastructure

- **LLM grading service** (F4)
- **Spaced-review scheduler** (F5)
- **Authoring pipeline** — markdown + git, with generator publishing to DB
- **Cost monitoring** for LLM grading calls
- **Grader regression discipline** — automated runner against ground-truth set

### Locked architectural decisions

| Q | Decision |
|---|---|
| Q1 Mobile platforms | **Android-only for v1**; iOS deferred to a v2 conversation |
| Q2 Authoring source-of-truth | **Markdown + Git**. Calibration tags as front-matter, sources as YAML. CI generates DB content. Version-controlled, peer-reviewable. |
| Q3 LLM provider | **Pick one and own it.** *Anthropic Claude Sonnet 4.6* preferred (prompt caching on rubric, calibrated-uncertainty training, native structured outputs, escalation path to Opus 4.7 for hardest rubrics). Final provider confirmation pending. |
| Q4 Web surface in v1 | **Read-only web preview.** Shareable unit links for marketing / word-of-mouth. No full web app. |
| Q5 Offline strategy | **Read-offline, decide-online.** Units cache; LLM grading requires network. |

---

## Cross-cutting decisions

### T2 — Grader calibration

The wedge (*decision-grade*) only delivers if the grader is trustworthy. The design problem isn't "build a grader"; it's "build a grader you can prove isn't lying."

| Choice | Decision |
|---|---|
| A. Rubric structure | **Per-criterion checklist** (Met / Not Met for each criterion). No holistic score. |
| B. Confidence surfacing | **Flagged-or-graded.** If the grader is uncertain anywhere, the answer is *flagged* "review needed" and the canonical answer is shown instead of a pass/fail. When graded, per-criterion confidence is visible. Directly serves P2's "we don't know yet." |
| C. Calibration detection | **Ground-truth regression set + user feedback** (*"was this grade fair?"* thumbs after grading). |
| D. Hallucination guardrails | **All four:** strict JSON / tool-call schema; source-grounding (grader sees unit content + sources); answer-quote requirement (grader must quote user text it's grading on); structured tool-call output. |
| E. LLM provider | **Anthropic Claude Sonnet 4.6** with prompt caching on the rubric. Escalation path to Opus 4.7 for hardest rubrics, without re-architecting. |

**Discipline (not a feature).** Every published unit ships with regression-set pairs (tiered: `>= 20` flagship, `>= 10` standard). No regression set, no publish.

**Completion bar (T2 amendment, 2026-06).** Originally "grading is completion": any graded answer completed the unit. Live testing showed this let a junk answer ("typing anything") complete a unit, undercutting the path's meaning. Amended: **a submission completes the unit only when at most one rubric criterion is Not met and the answer is not flagged** (3-criterion unit → ≥ 2 met; 4-criterion → ≥ 3). This is deliberately *not* a holistic score — choice A stands: the user always sees the full per-criterion calibration, and the bar is expressed in criteria, never as a percentage grade. Below the bar the unit stays in progress and the learner revises and resubmits; nothing is persisted for non-completing attempts. A completed unit is never un-completed by a later, worse re-submission.

**UX flag (TT2).** LLM grading takes 2–8 seconds. **Stream the rationale as it generates** (Anthropic supports streaming). User sees a useful loading state, not a bare spinner.

### T1 — Editorial bottleneck

Authoring throughput, not engineering, is the real pacing constraint. Engineering F1–F7 is roughly 3 months. Authoring 20 high-quality units with rubrics, sources, calibration tags, and regression sets is **3–6 more months in parallel**.

| Choice | Decision |
|---|---|
| Q1 v1 path scope | **20 units.** Down from initial 30 floated. Pedagogically sufficient; doesn't feel thin. |
| Q2 Author model | **Founder + LLM-assisted drafting.** LLM produces first drafts from outline + sources. Founder verifies, sources, calibration-tags, ships. Compatible with P2 *if and only if* every claim is human-verified before publish. |
| Q3 Pipeline tooling | **Schema linter + markdown templates** for v1 (CI check that every unit has all 9 slots, calibration tags valid, sources have URLs/dates, rubric well-formed). Add **regression-set runner** before the path is half-authored. Source resolver and preview renderer deferred. |
| Q4 Content reuse | **Hybrid.** Reuse existing glossary entries as raw material for the *bite*. Author the wedge slots fresh — trade-off framing, decision prompt, rubric, regression set. Roughly 30–40% reuse on bite/depth slots, 0% reuse on wedge slots. |
| Q5 Regression-set discipline | **Tiered hold.** `>= 20` flagship, `>= 10` standard, **never zero.** Documents the discipline without making perfection the enemy of shipping. |

**Reality (TR1).** Calendar from now to v1 launch is mostly authoring time, not engineering time. The schedule isn't *"engineering done in 3 months, then ship."* It's *"engineering done in 3 months; authoring takes 3–6 more months in parallel; ship when both finish."*

**Discipline (TR2).** LLM-assisted drafting is fine. LLM-drafted *published claims* are not. The discipline must be explicit: every claim must have a human-verified source attached **before** it leaves the editor. The LLM produces drafts; the human ships.

---

## Naming

**Name: Perpenda.** *(Originally codenamed Libella; renamed during the Phase 4 trademark pass — see below.)*

Latin *perpendere* — "to weigh carefully, to ponder" (English *perpend*, archaic, = to weigh in the mind). It stays in the same balance / level / weigh family as the original codename — so the level mark and design carry over untouched — but it names directly what the product builds: **deliberate, weighed product decisions**. Object-as-metaphor for **P2 (calibrate, don't bluff)** — the moat.

**Status.** Zero detections across USPTO, EUIPO/TMview, WIPO, and the Play Store; `perpenda.com` registered. Final professional trademark clearance with counsel before public launch (a database search is not a substitute for counsel).

**Why not Libella (the original codename).** *Libella* (Latin: small balance / spirit level) carried the same calibration metaphor, but the trademark pass found **Libelle IT Group** too close — one letter apart, near-homophone, adjacent tech space — so the name was set aside before any public use.

**Cut from the shortlist (with reasons, for posterity):**

- *Libella* — set aside; *Libelle IT Group* collision (above).
- *Plumb* — `useplumb.com` is a funded AI workflow product
- *Cogent / Cogens* — Cogent is already an AI learning platform; namespace saturated
- *Verdict* — `verdictai.ai`, Haize Labs Verdict framework, multiple legal-AI products
- *Caliber / Calibr* — `calibr.ai` is "AI-Powered Learning Platform" (same category)
- *Bearing* — `bearing.ai` ($10M maritime AI)
- *Trutina* — `trutina.ai` is a real AI consultancy
- *Regula* — `regula.ai` + Regology + Regulativ.ai (regulatory-AI wall)
- *Veredico* — phonetic collision with Verido (existing iOS+Android app)
- *Veritha* — clean namespace, but Veritas ($2.5B) phonetic shadow
- *Sententia* — exact-name collision (Sententia IT solutions) plus crowded "Sent-" cluster

---

## Open sub-decisions / what remains

These are not blockers for the strategy. They are separate workstreams that can use this document as their anchor.

1. **Trademark / domain availability check** for Perpenda. `perpenda.com` registered; zero detections on USPTO / TMview / WIPO / Play. Final attorney clearance still required before public launch.
2. **LLM provider final confirmation** under Architecture Q3. Anthropic Claude Sonnet 4.6 is the strong preferred choice; final lock pending.
3. **T1 staffing path.** Can the founder reasonably author 20 units + regression sets over 4–6 months solo? If not, Q2 (author model) needs revisiting — paid contributor or stricter LLM-assist discipline.
4. **Repo cleanup pass.** The current Android app, backend, and `ROADMAP.md` reflect earlier framings. A separate audit should mark which existing code/data is reusable, which is dead, and which needs to be rebuilt against this strategy.
5. **Content authoring kickoff.** Schema linter + markdown templates (Q3 of T1) is the first engineering deliverable that unblocks authoring. Should start in parallel with reshaping the data model.

---

## Notes on durability

- The **wedge** (P1 + P2 primary) and the **soul** (learning, not retrieval) are the load-bearing decisions in this document. Change them with care; everything else flows from them.
- Specific phrasings, rankings, and unit counts may evolve as the team learns from real users. The locked decisions are the *positions*, not the exact wording.
- "Why now" is baked into the Vision sentence (*"the decisions their teams now have to make"*) and into the Problem statement. If "AI mandatory in product roadmaps" stops being true, this strategy needs revisiting.
