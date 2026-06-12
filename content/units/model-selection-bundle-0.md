---
id: model-selection-bundle-0
slug: model-selection
path_id: llm-systems-for-pms
position: 5
prereq_unit_ids:
  - evals-bundle-0
status: published
definition: Model selection is the synthesis decision — picking which model class to ship a feature on by weighing capability tier against cost and latency, anchored to performance on the load-bearing task as measured by your eval discipline, not by the vendor spec sheet.
calibration_tags:
  - claim: "Frontier providers publish three or four capability tiers, and the per-tier ordering (top crushes reasoning, mid is quality-at-acceptable-cost default, small handles high-volume / structured) is roughly stable across providers."
    tier: settled
  - claim: "Per-tier cost typically spans roughly an order of magnitude top-to-bottom on input pricing; the gap compounds at production volume."
    tier: settled
  - claim: "The capability gap between tiers is task-dependent — a small model might match a large one on some tasks and not others."
    tier: settled
  - claim: "Cross-tier hybrid routing reliably reduces cost-per-quality vs single-tier deployment for production features with mixed query difficulty."
    tier: contested
  - claim: "Whether next-generation small-tier models will close the capability gap enough to obviate the hybrid pattern for most product features is unsettled."
    tier: unsettled
sources:
  - url: "https://platform.claude.com/docs/en/about-claude/models/overview"
    title: "Anthropic — Models overview (Claude API docs)"
    date: 2026-05-11
    primary_source: true
  - url: "https://platform.claude.com/docs/en/about-claude/pricing"
    title: "Anthropic — Pricing (Claude API docs)"
    date: 2026-05-11
    primary_source: true
  - url: "https://openai.com/api/pricing/"
    title: "OpenAI — API pricing"
    date: 2026-05-11
    primary_source: true
  - url: "https://ai.google.dev/gemini-api/docs/pricing"
    title: "Google — Gemini API pricing (developer docs)"
    date: 2026-05-11
    primary_source: true
rubric:
  - text: "Names model selection as a multi-axis trade-off (capability vs cost vs latency) AND treats it as a measurement decision anchored to performance on the load-bearing task at projected scale — not a spec-sheet pick."
  - text: "Identifies a concrete failure mode of single-axis model selection — e.g., picking by cost alone, picking by capability alone, or picking by vendor benchmark."
  - text: "Explains the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., cost-only misses capability-tier breakpoints where the load-bearing-task quality collapses; capability-only hits cost-at-volume cliffs because per-call cost scales linearly with production volume and PMs systematically under-anticipate projected scale; vendor-benchmark-only misses regressions on the load-bearing task that only show up in your own eval."
  - text: "Distinguishes which model class is load-bearing in which regime — Opus-class for hard reasoning and high-stakes single-shot decisions; Sonnet-class as the quality-at-acceptable-cost default; Haiku-class for high-volume / latency-critical / first-pass-triage workloads; and recognizes the cross-tier hybrid pattern (route by query difficulty — e.g., small model on the bulk, escalate to larger model on specific signals) when no single tier fits the full cost/quality envelope."
---

# Model selection

## Trade-off framing

- **When this matters:** any feature shipping on an LLM where
  the team has to commit to a capability tier (or a routing
  strategy across tiers) and defend the choice to both
  engineering ("is the quality good enough?") and finance
  ("is the cost sustainable?"). Model selection is the
  decision that ties together everything from Units 1–4 —
  token economics, context-budget arithmetic, latency
  axes, eval discipline — into a single shipping call.

- **When this breaks:** when the team picks by spec sheet
  instead of by measurement. Three failure shapes recur:
  (1) cost-only selection misses capability-tier breakpoints
  where the load-bearing-task quality collapses below the
  feature's value floor; (2) capability-only selection hits
  cost-at-volume cliffs because per-call cost scales
  linearly with production volume and PMs systematically
  under-anticipate projected scale (day-1 volume is always
  smaller than year-2 volume); (3) vendor-benchmark
  selection misses regressions on the load-bearing task
  that only show up in your own eval.

- **What it costs:** the discipline to scope a bake-off
  rather than pick from intuition; the patience to measure
  capability per tier on the load-bearing task at projected
  scale (not at prototype volume); the willingness to ship
  a hybrid routing strategy when no single tier fits the
  cost/quality envelope; and the math habit to annualize
  per-call cost at projected production volume before
  bringing a number to finance.

## 90-second bite

**Model selection** is the synthesis decision — which model
class to ship a feature on. The trap is treating it as a
*spec-sheet* pick when it's a *measurement* pick.

Three axes, all measured against the load-bearing task at
production volume:

1. **Capability tier.** Opus / Sonnet / Haiku class (or the
   equivalent at any provider). A 70%-vs-85% bug-catch
   difference between Sonnet and Opus isn't a 15-point gap
   on paper — it's whether your feature delivers on its
   own value proposition. *Wrong move:* picking the larger
   model because it "looks safer" without checking whether
   the smaller one already crosses your quality bar on the
   task you ship for.

2. **Cost per unit-of-output.** Per-token pricing (Unit 1)
   times what you stuff into context (Unit 2). The trap is
   sub-linear thinking: a 5× per-call gap between Haiku and
   Opus becomes 5× your monthly bill at scale, not 5× one
   query. *Wrong move:* picking the high-capability tier
   without doing the arithmetic at projected volume.
   $0.08/call × 500/day × 250 days ≈ $10k/year. That's the
   real number.

3. **Latency profile.** Multi-axis (Unit 3) — TTFT, total
   completion, streaming. Smaller models are faster on
   every axis. *Wrong move:* picking the fast tier because
   the spec sheet looks good, without checking whether
   speed cost you the load-bearing task.

The PM call is figuring out which axis your worst case
bites on, then evaluating candidates against your
load-bearing task using your eval discipline (Unit 4). The
right answer almost always requires a bake-off — at least
two candidates, measured by your own eval, at projected
scale.

And often the right answer isn't *one* tier — it's a
**cross-tier hybrid**: small model for first-pass triage,
escalate to large model for the cases that need it. That's
the synthesis move the spec sheet can't help you with.

If you're picking a model, you're not picking a tier —
you're picking a measurement strategy that ends in a tier
choice (or a hybrid).

## Depth

Model selection is a synthesis decision because every axis
it touches is taught by another unit. What this unit adds
is the integration — how to weigh tokens (Unit 1), context
(Unit 2), latency (Unit 3), and eval discipline (Unit 4)
against a candidate set of model classes and arrive at a
defensible shipping choice.

**Three measurements PMs should ask their eng team for, by
name** — and the bake-off you scope is the answer to all
three:

- **Per-tier quality on your load-bearing task.** Not the
  vendor benchmark. Your eval set, your rubric, your task
  definition. Two candidates minimum (almost always the
  next tier up and the next tier down from the team's
  intuition). Unit 4's layered eval method is how you
  measure.
- **Per-call cost at production volume.** Token-counted
  input × per-token input price + token-counted output ×
  per-token output price, multiplied by daily volume ×
  annual workdays. The arithmetic looks trivial until
  volume scales 10×.
- **TTFT and total completion at p50 / p95.** Both metrics,
  both percentiles. Most *"is the latency acceptable?"*
  answers fall apart at p95 even when p50 looks fine.

A PM scoping a model selection with only one of these is
scoping with one number where the decision needs three.

**The capability tier choice.** Frontier providers publish
three or four tiers at a given time — at Anthropic that's
Haiku, Sonnet, Opus; at OpenAI it's the GPT-4.1 nano/mini/
full split (and the o-series for reasoning); at Google
it's Gemini Flash / Pro / Ultra. Across providers, the
per-tier capability ordering is roughly stable: top tier
crushes hard reasoning and multi-step instructions; mid
tier is the quality-at-acceptable-cost default for most
production features; small tier handles structured output,
high-volume routing, and latency-critical surfaces.
**The specific gap between tiers is task-dependent**,
which is why you have to bake off — a Haiku-class model
might catch 80% of what Opus catches on one task and 30%
on another.

**The cost math at scale.** Per-tier pricing (as of
2026-05) at the frontier sits roughly in these bands —
top tier around $2–5 per million input tokens; mid tier
$1–3; small tier $0.10–1. Output tokens typically priced
~5× the input rate. Multiplied by production volume, the
gaps compound: a feature that costs $0.015 per call on
Haiku-class and $0.08 on Opus-class is a ~5× monthly-bill
ratio at the same volume. Sub-linear thinking is the
failure shape — *"$0.08 doesn't sound like much"* becomes
$10,000/year at 500 calls/day. **Always do the annualized
math at projected scale**, not at current volume.
*(Pricing rough as of 2026-05; verify against current
provider docs before quoting to finance — pricing moves
faster than this unit republishes.)*

**The latency profile.** Per Unit 3, latency has three
sub-axes (model-size, streaming, total-completion).
Within the capability-tier decision, model-size is mostly
fixed by your tier pick. Streaming and total-completion
remain levers. Use Unit 3's framework here — there's no
separate Unit 5 latency content beyond *"smaller tier =
faster on every sub-axis."*

**The cross-tier hybrid pattern.** When no single tier
fits the cost / quality envelope, route by query
difficulty. Common shapes:

- **First-pass triage.** Small model handles 80% of cases
  that don't need depth; escalate to mid or large model
  when the small model's confidence is low or specific
  signals fire (e.g., security keywords in a code-review
  bot, complex reasoning markers in a Q&A bot).
- **Structured-output offload.** Large model for the
  reasoning step; small model for formatting the output
  into JSON or filling a structured response. Cuts cost
  without sacrificing the reasoning quality.
- **Speculative decoding / draft-and-verify.** Less
  PM-visible — small model drafts, large model verifies.
  Engineering decision more than product decision; mention
  but don't dwell.

The hybrid is the answer when the bake-off shows *"the top
tier is overkill for 80% of queries but the mid tier
misses the 20% that matter."* It's the synthesis move that
no single-tier choice can make.

**Cross-vendor caveat.** Bake-offs across providers aren't
apples-to-apples. Different tokenizers (Unit 1) mean
different per-call costs at the same input. Different
rate limits and region availability affect production
deployability. The right comparison is *cost-per-quality
on your load-bearing task at your target region*, not the
spec-sheet token-price.

## Decision prompt

Your team is shipping a code-review bot for internal PRs.
The bot reads a diff (avg 200 lines, max 2000) and posts a
comment with bug catches, style suggestions, and security
flags. Engineering has prototyped three model paths and
run them against a 100-PR eval set:

- **(A) Opus-class** — catches 85% of real bugs, 92% of
  security flags. ~$0.08/PR. 12s response.
- **(B) Sonnet-class** — catches 70% of real bugs, 78% of
  security flags. ~$0.05/PR. 4s response.
- **(C) Haiku-class** — catches 45% of real bugs, 38% of
  security flags. ~$0.015/PR. 1.5s response.

Volume: 50 PRs/day now, plans to scale to 500/day
post-launch. Finance is asking *"is this sustainable?"*
Engineering is asking *"is the bug-catch rate good
enough?"* The CEO wants to know which to ship for the
launch demo next week.

How do you scope the decision? What ships, and how do you
defend the choice to all three stakeholders? Walk through
which axis is load-bearing for this surface, where each
single-tier pick would get the trade wrong, and where
you'd be willing to be wrong.
