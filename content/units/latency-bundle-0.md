---
id: latency-bundle-0
slug: latency
path_id: llm-systems-for-pms
position: 3
prereq_unit_ids:
  - context-window-bundle-0
status: published
definition: Latency is the time from "I asked" to "the answer feels useful" — and on most product surfaces what matters isn't total response time, it's time-to-first-meaningful-output.
calibration_tags:
  - claim: "Frontier provider latency tiers span roughly an order of magnitude — sub-second TTFT on small models to 8+ seconds total completion on large ones."
    tier: settled
  - claim: "TTFT (time-to-first-token) and total completion time are separately measurable and separately optimizable — they trade against each other through model-size and streaming choices."
    tier: settled
  - claim: "Streaming output via Server-Sent Events is supported by all major providers and changes user-perceived speed without changing total response time."
    tier: settled
  - claim: "Smaller models trade quality for speed on the load-bearing task — quality loss is task-specific, not uniform across tasks or model generations."
    tier: contested
  - claim: "Whether next-generation small-model latency improvements will obviate the streaming-vs-blocking distinction is unsettled."
    tier: unsettled
sources:
  - url: "https://platform.claude.com/docs/en/build-with-claude/latency"
    title: "Anthropic — Reduce latency (Claude API docs)"
    date: 2026-05-10
    primary_source: true
  - url: "https://platform.claude.com/docs/en/build-with-claude/streaming"
    title: "Anthropic — Streaming Messages (Claude API docs)"
    date: 2026-05-10
    primary_source: true
  - url: "https://platform.openai.com/docs/models/gpt-4.1"
    title: "OpenAI — GPT-4.1 model reference (latency tier)"
    date: 2026-05-10
    primary_source: true
  - url: "https://ai.google.dev/gemini-api/docs/text-generation#stream"
    title: "Google — Gemini API streaming (developer docs)"
    date: 2026-05-10
    primary_source: true
  - url: "https://platform.claude.com/docs/en/build-with-claude/prompt-caching"
    title: "Anthropic — Prompt caching (Claude API docs, latency interaction)"
    date: 2026-05-10
    primary_source: true
rubric:
  - text: "Names latency as a multi-axis trade-off (model-size vs streaming vs total-completion) AND treats user-perceived speed as the load-bearing metric, not raw end-to-end seconds."
  - text: "Identifies a concrete failure mode of treating latency as a single number — e.g., picking a fast model that costs accuracy on the load-bearing task; shipping streaming UX where the output is consumed atomically so partial output is unusable; or optimizing TTFT when total completion is what gates the user's next action."
  - text: "Explains the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., a smaller/faster model trades capability for speed, so quality drops on the load-bearing task; or TTFT and total-completion are different clocks, so optimizing the wrong one does nothing for a user whose next action waits on the full response. (Distinct from the regime distinction in the next criterion: this is the causal 'why', not the when-to-stream call.)"
  - text: "Distinguishes a regime where streaming UX is the right default (output is read incrementally — chat, explanation, summary) from a regime where it isn't (output is consumed atomically — JSON for downstream code, function-calling, structured form-fill)."
---

# Latency

## Trade-off framing

- **When this matters:** any feature where the user is waiting for
  the model to respond — which is most of them. The PM-visible
  metric isn't a single "how fast?" number; it's a multi-axis call
  between *which* model, *how* the output is delivered, and *what*
  the user is going to do with it. Scoping a feature by *"we need
  it under 3 seconds"* without naming which 3 seconds (TTFT, p50,
  p95, full completion) ships the wrong architecture.

- **When this breaks:** when the team treats latency as a single
  number to optimize. Three failure shapes recur: (1) picking a
  fast model that loses accuracy on the *load-bearing task* the
  feature exists for; (2) shipping streaming UX on a surface where
  the output is consumed atomically, so the streaming buys nothing;
  (3) optimizing TTFT when total completion is what gates the
  user's next action. Each comes from collapsing the three axes
  into one.

- **What it costs:** the discipline to name three measurements —
  TTFT, tokens-per-second after first token, total completion at
  p50 and p95 — and pick the one that's load-bearing for the
  surface; the willingness to ship streaming as the default on
  chat-shape features even when "total time" looks slow on paper;
  and the patience to verify quality on the load-bearing task
  before swapping models for speed.

## 90-second bite

**Latency** is the time from *"I asked"* to *"the answer feels
useful"* — and the trap is treating it as a single number to
optimize.

Most PMs scope latency in seconds: *"we need it under 3 seconds."*
That number can't be answered honestly without naming **which 3
seconds**. The right model has three axes, not one:

1. **Model-size axis.** A smaller model returns the full answer
   in 1–2s; a larger model takes 5–10s. The trade is speed against
   quality on whatever your load-bearing task is. *Wrong move:*
   picking the fast model and accepting accuracy loss on the task
   you're shipping for.

2. **Streaming axis.** A streaming response shows the first tokens
   within ~200ms even on a slow model. To the user it feels snappy
   regardless of total time. UX cost is roughly zero, eng cost is
   low. Streaming is load-bearing for chat-shaped surfaces — and
   it's surprisingly absent from most PM AI literacy.

3. **Total-completion axis.** If your user can't act until the
   full output arrives — JSON for downstream code, a function-call
   result, a filled-out form — streaming buys nothing. The metric
   that matters is when the *full* response lands, not when the
   first token does.

The PM call is figuring out which axis is load-bearing for the
surface you're shipping. Chat-shaped feature → optimize streaming,
accept slower total. Atomic output → optimize total completion,
model-size becomes the lever. Mixed → pick the axis your worst
case bites on.

*"Snappy"* doesn't mean fast. It means the user feels in motion,
not in queue. Those are different problems with different
solutions.

If you're scoping a latency target, you're picking which axis to
spend on, not how many seconds you're allowed.

## Depth

The headline numbers shift fast; the trade-off they expose doesn't.
As of mid-2026, frontier providers publish per-tier latency targets
that span roughly an order of magnitude — from sub-second
time-to-first-token on the smallest models to 8–10+ seconds for
full output on the largest. The constants worth a PM's attention:
TTFT (time-to-first-token) and total completion are different
metrics, both are user-facing, and they trade against each other
through model-size and streaming choices.

**Three measurements PMs should ask their eng team for, by name.**
Not *"how fast?"* but:

- **TTFT (time-to-first-token).** How long until the first word
  lands. Load-bearing for perceived speed on chat-shaped surfaces.
- **Tokens-per-second after first token.** How fast the response
  unspools once it starts. Load-bearing for read-along UX.
- **Total completion time at p50 and p95.** When the full output
  is available. Load-bearing for atomic-output use cases and for
  setting timeout budgets.

A PM scoping a feature with only *"average latency"* is scoping
with one number where they need three.

**The model-size lever.** Smaller models (Anthropic's Haiku,
OpenAI's GPT-4.1 mini/nano, Google's Gemini Flash) hit TTFT in the
200–500ms range and complete short outputs in 1–2 seconds. Larger
models (Anthropic's Sonnet/Opus, GPT-4.1, Gemini Pro) take
500ms–1s for TTFT and 4–10+ seconds for full completion at typical
input sizes. The trade isn't just speed-for-quality in the
abstract — it's *speed for quality on the load-bearing task you're
actually shipping for*. A model that's 5× faster but loses 10% on
action-item extraction is the wrong choice for an action-item
product, even if the latency feels great.

**The streaming lever.** Every major provider supports streaming
output via Server-Sent Events; the API surface is well-documented
and most generic-chat-shape products use it. The UX win is
asymmetric: on a slow model, streaming changes *"user waits 6
seconds, sees nothing"* into *"user reads along starting at
200ms."* Same total time, completely different feel. Eng cost is
low (a few hours of integration), so the question is almost always
*should we stream?* not *can we?*

**The atomic-output trap.** Streaming buys nothing when the user
can't act on partial output. Three patterns where this bites:

- **Structured output for downstream code** — JSON parsed by your
  client, function-calling result executed by your agent. The
  first token is unusable; only the full document matters.
- **Form-fill** — populating fields in a UI. Showing a half-filled
  form is worse UX than a brief loading spinner followed by the
  full result.
- **Tool-use chains** — the LLM's output is an intermediate step
  in a multi-call pipeline. The user sees the *final* answer;
  intermediate latency is summed, not parallelized.

For these surfaces, optimize total completion. Model-size becomes
the primary lever; streaming is irrelevant.

**Caching helps, but on the input side.** Prompt caching on the
system prompt or RAG context cuts the *cost* of repeated long
prefixes substantially, and on some providers it modestly cuts
TTFT too — but the effect is smaller and less reliable than the
model-size or streaming choice. *Don't conflate cost optimization
with latency optimization.*

**Cross-vendor caveat.** Latency benchmarks aren't apples-to-apples
across providers — different infrastructure, different streaming
protocols, different region availability. The right comparison is
*latency-per-quality on your actual prompt shape on your target
region*, not the spec-sheet numbers.

## Decision prompt

Your team is shipping a PM-facing *"meeting summarizer."* Input: a
30-minute transcript (~8k tokens). Output: three sections — TL;DR,
decisions made, action items.

Engineering prototyped two paths:

- **(A) Sonnet-class, blocking:** ~6s end-to-end, high quality on
  action-item extraction.
- **(B) Haiku-class, streaming:** ~1.5s to first paragraph, total
  ~3s, but action-item quality drops noticeably (misses 1–2 items
  per transcript).

The exec demoing this internally next week wants it *"snappy."*
How do you scope the decision, and what do you ship?

Walk through which latency axis is load-bearing for this surface,
where each prototype gets the trade wrong, and what you'd ship
instead — being specific about how you'd justify it to the exec.
Where would you be willing to be wrong?
