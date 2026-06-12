---
id: hallucination-bundle-0
slug: hallucination
path_id: llm-systems-for-pms
position: 7
prereq_unit_ids:
  - prompt-design-bundle-0
status: published
definition: Hallucination is the LLM's structural tendency to generate plausible-sounding output that isn't grounded in the input or in reality — and reliability is the discipline of designing features to make hallucination visible, attributable, and bounded, not absent.
calibration_tags:
  - claim: "Every shipped LLM feature has a non-zero hallucination rate; rates can be reduced but not driven to zero across all task shapes."
    tier: settled
  - claim: "Grounding (RAG / source-prompt content) reduces factual hallucination rates for tasks where the answer is in retrievable source material."
    tier: settled
  - claim: "Structured output validation (T2-D-style: required answer-quotes, schema constraints) catches a meaningful fraction of citation and capability hallucinations before they reach users."
    tier: settled
  - claim: "The exact rate reduction achievable from each mitigation lever is task-dependent and not generalizable — public benchmarks often disagree with production-task measurements."
    tier: contested
  - claim: "Whether next-generation models with better grounding and self-verification will reduce hallucination to a level where containment becomes unnecessary for most product features is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2202.03629"
    title: "Survey of Hallucination in Natural Language Generation (Ji, Lee, Frieske, Yu, Su, Xu, Ishii, Bang, Madotto, Fung)"
    date: 2022-02-08
    primary_source: true
  - url: "https://arxiv.org/abs/2005.11401"
    title: "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks (Lewis, Perez, Piktus, Petroni, Karpukhin, Goyal, Küttler, Lewis, Yih, Rocktäschel, Riedel, Kiela)"
    date: 2020-05-22
    primary_source: true
  - url: "https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-hallucinations"
    title: "Anthropic — Reduce hallucinations (Claude API docs)"
    date: 2026-05-12
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/optimizing-llm-accuracy"
    title: "OpenAI — Optimizing LLM Accuracy (developer docs)"
    date: 2026-05-12
    primary_source: true
  - url: "https://ai.google.dev/gemini-api/docs/grounding"
    title: "Google — Grounding with Google Search (Gemini API docs; canonical grounding-mitigation reference, not a hallucination overview)"
    date: 2026-05-12
    primary_source: true
rubric:
  - text: "Names hallucination as a structural base-rate problem (not a bug to be eliminated) AND treats reliability as a multi-axis design discipline (detection vs mitigation vs containment) anchored to the feature's actual cost-of-failure — not as a 'improve the model' exercise."
  - text: "Identifies a concrete failure mode of single-axis hallucination management — e.g., mitigation-only, detection-only, containment-only, or refusing to ship without 100% accuracy."
  - text: "Explains the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., mitigation-only collapses at scale because a 'small' rate compounds with volume (0.5% × 10k calls/day = 50 false outputs/day, enough to erode trust); detection-only without mitigation is just measuring the bleed; containment-only without detection means failures go uncounted; refusing to ship without 100% accuracy treats the base rate as if it were eliminable when it is structural."
  - text: "Distinguishes which approach is load-bearing in which regime — detection load-bearing when shipping into a new domain or scaling exposure (you need to know the rate); mitigation load-bearing when the rate itself is the unit-economics or quality problem; containment load-bearing when the cost of an individual hallucination is high (legal liability, financial loss, safety); and recognizes that high-stakes features require all three layered (the PM-default for any feature where a single hallucination can cause non-recoverable damage)."
---

# Hallucination + reliability

## Trade-off framing

- **When this matters:** any feature where the LLM's output
  is taken by a user (or a downstream system) as a factual
  claim. That includes Q&A bots, summarizers, search
  assistants, code assistants that explain APIs,
  classifiers whose verdicts users act on, agents that
  trigger workflows. The PM-visible question isn't *"will
  it hallucinate?"* (yes, sometimes — that's structural)
  but *"how do we design around the residual rate so that
  failures are detectable, infrequent, and safely
  contained?"*

- **When this breaks:** when the team treats hallucination
  as a bug to fix instead of a base rate to manage.
  Three failure shapes recur: (1) mitigation-only thinking
  ("just improve the model / add RAG / pick a bigger
  tier") collapses at scale because rates compound with
  volume — a 0.5% rate at 10k calls/day is 50 false
  outputs daily, ~12,500 per year; (2) detection-only
  thinking measures the bleed but doesn't reduce it or
  contain the damage; (3) containment-only thinking makes
  failures safe but lets the rate climb because it's not
  being counted.

- **What it costs:** the discipline to scope reliability
  as a multi-axis investment (detection + mitigation +
  containment) rather than a single fix; the willingness
  to annualize the failure count at projected scale
  before deciding whether a rate is "acceptable"; the
  patience to layer mitigations (no single tool gets the
  rate to acceptable on its own for high-stakes features);
  and the discipline to design containment paths
  (refusal, confidence signaling, blast-radius limits)
  proportional to the cost-of-one-hallucination on the
  specific feature.

## 90-second bite

**Hallucination** is the LLM's structural tendency to
generate plausible output that isn't grounded in the input
or in reality. Every shipped LLM feature has a non-zero
hallucination rate. The trap is treating it as a *bug to
fix* instead of a *base rate to manage*.

Three axes, all needed for any feature where hallucination
matters:

1. **Detection.** How do you know when it's happening?
   Pre-shipment: eval set, source-quote validation,
   structural guardrails. Post-shipment: audit sampling,
   user reports, regression on golden cases. *Wrong move:*
   shipping without a way to count the failure rate. You
   don't know if you're at 0.5% or 5% until you can
   measure.

2. **Mitigation.** What reduces the rate? Grounding
   (RAG, source-prompt content), instruction tightening,
   model-tier selection (Unit 5), structural output
   validation. Mitigation compounds — no single tool
   reduces hallucination by enough; layering matters.
   *Wrong move:* picking one mitigation lever (*"we'll
   just add RAG"*) and treating the rate as solved.

3. **Containment.** What makes the failure mode safe when
   it slips through? Refusal paths (*"I don't know"*),
   confidence signaling in the UI, blast-radius limits
   (the feature can't take destructive action on its
   own), human escalation thresholds. *Wrong move:*
   assuming detection + mitigation gets you to zero. They
   don't. Containment is what saves you when the residual
   rate bites.

PM trap: optimizing only for the rate. A 0.5% rate sounds
small until you do the volume math — at 10k calls/day
that's 50 false outputs daily, which is enough to erode
trust. The rate is real; the volume makes it real money.

The PM call is figuring out which axis is load-bearing
for *this* feature — measured against the **cost of one
hallucination**. Low-cost individual failures (a slightly
wrong answer in a tool that's clearly assistive) →
mitigation-led. High-cost individual failures (legal
liability, financial loss, safety) → containment-led.
Always need detection.

If you're shipping an LLM feature, you're not eliminating
hallucination — you're picking what you detect, what you
mitigate, and what you contain.

## Depth

Hallucination is what makes LLM features structurally
different from deterministic software. A SQL query either
returns the right row or fails loudly; an LLM call returns
a plausible-sounding answer whether or not the underlying
inference is grounded. That structural property is what
reliability engineering for LLM features has to design
around — and it changes which questions a PM has to ask
before shipping.

**Three measurements PMs should ask their eng team for,
by name:**

- **Hallucination rate on a representative eval set.**
  Not the vendor's benchmark — your own ground-truth set
  covering the load-bearing query shapes. The rate is
  task-dependent and shifts when the model, prompt, or
  grounding strategy changes. A pre-shipment number that
  doesn't sit on your actual workload is a number that
  won't survive production.
- **Post-shipment audit cadence.** What fraction of live
  outputs gets sampled and graded for hallucination, on
  what schedule, with what review path? An audit cadence
  of *"we'll check if users complain"* is no audit
  cadence; the rate stays unknown.
- **Cost-of-one-hallucination.** For each failure mode
  the feature could produce, what's the damage radius?
  Low-cost (a slightly wrong factual answer in an
  assistive tool) → mitigation-led design. High-cost
  (legal liability, financial loss, safety implications)
  → containment-led design. The right architecture
  follows from the answer.

**Three types of hallucination PMs encounter.** Different
from each other, different fixes:

- **Factual hallucination.** Model generates information
  that isn't true (a date, a number, a person's role).
  Most common; mitigated by grounding (RAG, source-prompt
  content) and detected by source-quote validation.
  Reduces under stronger grounding but doesn't go to zero.
- **Citation / grounding hallucination.** Model invents
  sources or misattributes claims to documents that don't
  say what's claimed. Particularly dangerous because the
  citation gives the answer false authority. Mitigated
  by requiring answer-quotes that the system can validate
  against the source documents — the T2-D guardrail
  pattern.
- **Capability hallucination.** Model claims to have done
  something it didn't (a tool call that didn't fire, a
  calculation it didn't run). The user trusts the claim
  because it's stated with confidence. Mitigated by
  structured output (tool-use API, function-calling)
  where the system can verify whether the action actually
  executed.

**Detection — what tools actually count failures.**
Offline: ground-truth eval set (Unit 4), source-quote
validation against retrieved documents, structural
validation of output shape (T2-D-style guardrails — does
the answer-quote actually appear in the source?). Online:
sampled audit (1–10% of production traffic graded by
humans or LLM-as-judge), explicit user-feedback paths,
regression on golden cases at every model/prompt change.
**Detection is the load-bearing axis when you're scaling
exposure** — going from 200 employees to 8,000 is a 40×
increase in failure surface that you need to count before
it bites.

**Mitigation — what reduces the rate.** Grounding (RAG
with high-quality retrieval, source-prompt content for
stable facts), instruction tightening (explicit refusal
conditions, output schema constraints), model-tier
selection (Unit 5 — larger tiers generally hallucinate
less on hard reasoning), output structure (force the
model to cite or refuse, never just answer). No single
mitigation tool gets the rate to acceptable on its own
for high-stakes features. **Layering matters.** A typical
production stack is RAG + answer-quote validation +
confidence-gated refusal + model-tier choice — four
mitigations stacked, not one.

**Containment — what makes residual failures safe.**
Refusal paths (the model returns *"I don't know based on
the available documents"* instead of guessing), confidence
signaling in the UI (when the model's confidence is low,
the UI shows it as low to the user rather than presenting
the answer with the same authority as a high-confidence
answer), blast-radius limits (the feature can't take a
destructive action — paying out a refund, sending an
email, modifying a record — without a human in the loop
on the boundary), human escalation thresholds (cases
where confidence is low or stakes are high get routed to
a human review queue). **Containment is the load-bearing
axis when the cost of one hallucination is high.**

**The volume-compound failure mode.** A 0.5% hallucination
rate sounds small until you do the math: at 10k calls/day,
that's 50 false outputs daily, ~12,500 per year.
Sub-linear thinking on rates is the standard PM failure
shape — same mechanism as Unit 5's cost-at-volume cliff.
**Annualize the rate at projected scale** before deciding
whether the rate is "acceptable." What's acceptable at
internal-beta volume is often not acceptable at full-
launch volume.

**Cross-axis caveat.** The three axes interact. Detection
without mitigation is just measuring the bleed; mitigation
without detection is hoping you're not bleeding;
containment without detection means failures go
uncounted. **Any feature where hallucination matters
needs all three layered.** The PM call is which axis is
*most* load-bearing for the cost-of-one-hallucination,
but none of the axes can be zero.

## Decision prompt

Your team is shipping a company internal-knowledge-base
Q&A bot. Employees ask questions like *"what's our
remote-work policy?"* or *"how do I expense international
travel?"* The bot uses RAG over a Confluence-style
document set (~50k internal documents). The eng team
picked Sonnet-class (Unit 5 decision), hybrid prompt
design (Unit 6 decision), passed the offline eval at 92%
accuracy on 200 ground-truth questions.

Three weeks after limited launch (200 employees, ~500
queries/day), legal flags that on 2 of 50 randomly
audited responses, the bot answered with policies that
don't exist in the source documents — including one
response that confidently described an exception to the
expense policy that would, if acted on, cost the company
money.

Eng says *"4% audit rate is industry-standard for
RAG-based Q&A."* CSAT is 4.1/5 (stable). The CEO wants to
ship to all 8,000 employees next quarter.

Should you ship at full scale, hold, or rebuild? How do
you scope the decision across detection, mitigation, and
containment — and how do you defend the call to legal,
engineering, and the CEO?
