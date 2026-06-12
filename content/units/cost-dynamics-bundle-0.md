---
id: cost-dynamics-bundle-0
slug: cost-dynamics
path_id: llm-systems-for-pms
position: 8
prereq_unit_ids:
  - hallucination-bundle-0
status: published
definition: Cost dynamics at scale is the second cost conversation — the one that happens after the model is chosen and the feature is shipping at volume, when finance starts asking whether the projected annualized bill is sustainable.
calibration_tags:
  - claim: "Prompt caching reduces input-token cost on cache hits to roughly 10% of the on-demand rate across major providers."
    tier: settled
  - claim: "Batch APIs discount both input and output tokens by ~50% in exchange for async processing (results within ~24 hours, typically faster)."
    tier: settled
  - claim: "Caching and batching discounts multiply rather than substitute — a batched cache-hit call pays the product of both discounts, not the sum."
    tier: settled
  - claim: "The exact discount achievable from committed-use / provisioned-throughput pricing is task-and-commitment-specific and not generalizable from published rates."
    tier: contested
  - claim: "Whether next-generation pricing models (per-task pricing, output-only billing, capability-tier subscriptions) will replace per-token billing for most product features is unsettled."
    tier: unsettled
sources:
  - url: "https://platform.claude.com/docs/en/build-with-claude/prompt-caching"
    title: "Anthropic — Prompt caching (Claude API docs)"
    date: 2026-05-13
    primary_source: true
  - url: "https://platform.claude.com/docs/en/build-with-claude/batch-processing"
    title: "Anthropic — Message Batches / batch processing (Claude API docs)"
    date: 2026-05-13
    primary_source: true
  - url: "https://platform.claude.com/docs/en/about-claude/pricing"
    title: "Anthropic — Pricing (Claude API docs)"
    date: 2026-05-13
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/batch"
    title: "OpenAI — Batch API guide (developer docs)"
    date: 2026-05-13
    primary_source: true
  - url: "https://ai.google.dev/gemini-api/docs/batch-mode"
    title: "Google — Gemini Batch API / batch mode (developer docs)"
    date: 2026-05-13
    primary_source: true
rubric:
  - text: "Names cost-at-scale as a multi-axis production-optimization problem (caching vs batching vs capacity) distinct from headline per-call cost (Unit 5) AND anchors the decision to annualized cost at projected volume, not at current-pilot volume."
  - text: "Identifies a concrete failure mode of single-axis cost optimization — e.g., caching-only, batch-only, or committed-use without volume forecasting."
  - text: "Explains the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., caching-only misses the batch-API discount on non-interactive workloads because sync-API rates apply per-call regardless of urgency; batch-only pays full input rate on every batched call because the batch discount applies after per-call costs are computed, so caching multiplies; committed-use without forecasting commits to a higher floor than usage warrants because committed-use is a take-or-pay obligation."
  - text: "Distinguishes which lever is load-bearing in which regime — caching for workloads with large stable prefixes (system prompts, RAG context, few-shot); batching for latency-forgiving / asynchronous workloads; committed-use for sustained high-QPS production where on-demand pricing exceeds the committed floor; AND recognizes that production-scale cost discipline almost always layers all three (caching + batching for the eligible workload share + committed-use sized to the steady-state floor), not picking one."
---

# Cost dynamics at scale

## Trade-off framing

- **When this matters:** any feature shipping at production
  volume where the API line item starts showing up in
  finance reviews. The PM-visible question isn't *"what's
  per-call cost?"* (that's Unit 5's tier-selection question)
  but *"is the projected annualized bill sustainable, and
  if not, which production-scale levers can we pull
  without changing the feature?"* The trap is treating
  cost as a one-time tier-selection decision rather than
  an ongoing multi-lever optimization that compounds with
  scale.

- **When this breaks:** when the team picks a single
  optimization lever and treats cost as solved. Three
  failure shapes recur: (1) enabling caching alone misses
  the batch-API discount on the latency-forgiving share
  of the workload — sync-API rates apply per-call
  regardless of urgency; (2) using batch-only without
  caching pays full input rate on every batched call,
  because batching applies *after* per-call cost is
  computed and the discounts multiply rather than
  substitute; (3) committed-use without volume
  forecasting commits to a higher floor than current
  usage warrants — committed-use is a take-or-pay
  obligation, not a discount you can stop paying for if
  volume drops.

- **What it costs:** the discipline to scope production
  cost as a multi-axis optimization (caching + batching +
  capacity) rather than a single fix; the willingness to
  annualize the bill at projected scale (not pilot scale)
  before deciding whether the current rate is acceptable;
  the patience to size committed-use against the
  steady-state floor rather than peak; and the
  arithmetic literacy to model how the three levers
  multiply rather than add when stacked.

## 90-second bite

**Cost dynamics at scale** is the second cost conversation
— the one that happens after the model is chosen and the
feature is shipping. Unit 5 picks the tier; Unit 8 figures
out whether the bill is sustainable at production volume.
The trap is treating cost as a one-time tier-selection
decision instead of an ongoing multi-lever optimization.

Three axes that decide whether cost-at-scale is
sustainable:

1. **Caching.** Prompt caching reuses stable prefixes
   (system prompts, RAG context, few-shot examples) at
   ~10% of the input-rate cost on cache hits. *Wrong
   move:* shipping without caching when 80% of every call
   is a stable system prompt — you're paying full-rate
   for tokens the provider serves at 10× discount.

2. **Batching.** Batch APIs typically discount by ~50% in
   exchange for async processing (results within 24
   hours). *Wrong move:* running latency-forgiving
   workloads through the sync API because *"we might
   need real-time someday"* — you're paying interactive
   rates for non-interactive work.

3. **Capacity.** Committed-use / provisioned-throughput
   pricing trades a fixed monthly floor for guaranteed
   capacity at lower per-token rates. Rate-limit retry
   strategies affect effective cost (failed retries still
   cost compute). *Wrong move:* hitting rate limits in
   production with no retry strategy or committed
   capacity — you ship outages, not features.

**The axes multiply, not substitute.** Caching applies
*before* batch discount: batched calls that are also
cache-hits compound the savings. The PM-default for
production at scale is almost always caching + batching
for the eligible workload share + committed-use sized to
the steady-state floor — not picking one lever.

The headline per-call cost from Unit 5 is the *starting
point*, not the answer. A feature that's "cheap on paper"
can be 5× more expensive than necessary in production
because nobody enabled caching or routed batch work
through the batch API.

If you're scoping a production cost question, you're not
picking one optimization — you're stacking three.

## Depth

The cost conversation in Unit 5 ends with *"pick the tier
and annualize the math."* Production reality starts a
different conversation, usually a few weeks post-launch
when finance pulls the API line item from the cloud bill.
The headline per-call cost is the *starting* point.
Production cost at scale is a different optimization
problem — one with levers that compound, can be wrong in
subtle ways, and almost always require stacking rather
than picking.

**Three measurements PMs should ask their eng team for,
by name:**

- **Stable-prefix ratio.** What fraction of every call is
  reused across calls? System prompt + RAG context +
  few-shot examples are typically stable; user query +
  per-call retrieved chunks are variable. A 4,000-token
  stable prefix in a 5,000-token call has an 80%
  stable-prefix ratio — and that's where caching is
  load-bearing.
- **Latency-tolerance per workload slice.** What fraction
  of the workload can wait minutes-to-hours for results
  without UX impact? Overnight classification, daily
  report generation, bulk document tagging, post-hoc
  analytics — all latency-forgiving. Real-time chat,
  in-the-loop suggestions, user-facing search — not.
  Batching applies to the first set.
- **Steady-state QPS at projected scale.** Not peak, not
  average — the sustained floor that runs 24/7.
  Committed-use pricing is sized against this floor;
  over-commit and you pay for unused capacity,
  under-commit and on-demand pricing eats the gains.

**Caching — what it does and when.** Major providers
(Anthropic, OpenAI, Google) all offer prompt caching with
similar mechanics: stable prefix tokens get marked as
cached, subsequent calls reusing the same prefix pay
roughly 10% of the input rate on cache hits. The cache has
TTL (typically minutes for ephemeral, longer for explicit
/ persistent variants) and prefix-match semantics (a
single token change in the cached portion invalidates the
cache for the rest of the call). Load-bearing when
stable-prefix ratio is high (>60%); diminishing returns
below that. **Anthropic documents up to ~10× reduction on
cache hits;** OpenAI and Google offer similar mechanisms
with provider-specific discount rates.

**Batching — what it does and when.** Batch APIs accept a
request now, return results within a stated window
(Anthropic: ~24h SLA, most batches complete within an
hour; OpenAI: similar; Google: similar). The discount is
typically ~50% on both input and output tokens.
Load-bearing when the workload is asynchronous-by-nature
— overnight classification, periodic re-tagging, bulk
migration, evaluation runs against large datasets. *Not*
load-bearing for real-time UX surfaces. **The discount
applies after per-call cost is computed**, which means
batching multiplies with caching: a batched call that's
also a cache-hit pays roughly 50% × 10% = ~5% of the
original input rate.

**Capacity / committed-use — what it does and when.**
Enterprise commitment pricing trades a fixed monthly
floor for guaranteed throughput at a discounted
per-token rate (typically 5-15% below on-demand,
depending on commitment size and length). Load-bearing
at sustained high QPS — features with a steady-state
floor of millions of tokens/day that don't fluctuate
wildly. **The math gates the decision:** committed cost
≤ on-demand cost at projected steady-state volume.
Over-commit and you pay for unused capacity;
under-commit and on-demand fills the gap at full price.

**Rate limits as effective-cost mechanism.** Hitting
rate limits in production isn't just a reliability
problem — failed retries cost compute on some providers,
and the operator pays for both the failed attempt and
the successful retry. A retry-and-backoff strategy with
circuit-breakers is the standard mitigation;
committed-use also raises the rate-limit ceiling on most
providers.

**The multiplication, made concrete.** Take the Unit 8
decision prompt's ticket classifier: $0.0225/ticket
on-demand sync. With caching (80% stable prefix at 10%
rate): input drops from $0.015 to ~$0.0042, total
~$0.012/ticket. Adding batching for the 70% latency-
forgiving slice (50% discount on those calls): blended
cost drops to ~$0.008/ticket. Adding 10% committed-use
as an additional discount on the post-caching+batching
effective rate: another ~$0.0007 off per ticket.
**Combined: $0.0225 → ~$0.007/ticket, about a 3×
reduction**, all without changing the model or the
feature. *(Numbers rough as of 2026-05; verify against
current pricing before quoting to finance.)*

**Cross-axis caveat.** The three levers interact in
predictable ways. Caching applies before batching
multiplies — a batched cache-hit pays the product of
both discounts. Committed-use applies on top of whatever
effective rate the call landed at after caching and
batching, as an additional percentage discount on the
resulting spend. The math compounds multiplicatively,
not additively: don't double-count savings in finance
projections by treating the eng-prototyped per-option
numbers as independent and adding them. Each lever
applies to a progressively smaller base, so the
in-isolation estimates overstate the on-top increment
when stacked.

## Decision prompt

Your team's customer-support ticket classifier is six
weeks post-launch. It auto-routes incoming tickets to
the right team queue (billing, technical, account,
refund-eligibility) using Sonnet-class inference. Each
ticket averages ~5,000 input tokens (the ticket text +
routing rules + 10 in-context examples) and ~500 output
tokens (the classification + reasoning trace).

Volume has grown faster than projected: 50,000
tickets/day, projected to 80,000/day by end of next
quarter. Finance is asking about the line item — the
current bill is **~$1,125/day ≈ $410k/year annualized**,
projected to ~$655k/year by Q-end.

Eng has three optimization paths prototyped:

- **(A) Enable prompt caching on the system prompt +
  routing rules + examples** (~4,000 of the 5,000 input
  tokens are stable). Estimated bill reduction: ~$540/day
  (~$135k/year). No UX impact.
- **(B) Route non-urgent tickets through the Batch API**
  (50% discount, 4–24 hour latency). ~70% of tickets are
  non-urgent (account questions, post-resolution
  feedback, low-priority billing). Estimated bill
  reduction: ~$390/day (~$95k/year). UX impact:
  non-urgent tickets get same-day classification instead
  of within-minutes.
- **(C) Negotiate committed-use pricing with the
  provider** for ~10% additional discount on all volume,
  in exchange for a 12-month commitment at projected
  scale. Estimated bill reduction: ~$110/day at current
  volume, scaling to ~$180/day at Q-end volume.

Finance wants to know which to do, and in what order.
Engineering wants to know which has the worst eng cost
/ risk. The CEO wants to know if there's a "do all
three" path. How do you scope the decision and defend
the call?
