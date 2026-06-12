---
id: evals-bundle-0
slug: evals
path_id: llm-systems-for-pms
position: 4
prereq_unit_ids:
  - latency-bundle-0
status: published
definition: Evals are the discipline of knowing whether your LLM feature is working before users tell you it isn't — and the trap is treating "eval" as a single thing instead of a layered strategy across four different methods that catch different failure shapes.
calibration_tags:
  - claim: "Production eval discipline almost always requires multiple methods; no single method catches all failure shapes."
    tier: settled
  - claim: "LLM-as-judge models exhibit systematic biases (position, verbosity, self-enhancement) and share blind spots with the model family being graded."
    tier: settled
  - claim: "Golden-set passes don't catch unknown regressions by definition — they only test known categories."
    tier: settled
  - claim: "Whether LLM-as-judge agreement with human eval is good enough to replace human eval for ambiguous-quality decisions is contested."
    tier: contested
  - claim: "Whether next-generation judge models will close the self-enhancement bias gap is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2306.05685"
    title: "Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena (Zheng, Chiang, Sheng, Zhuang, Wu, Zhuang, Lin, Li, Li, Xing, Zhang, Gonzalez, Stoica)"
    date: 2023-06-09
    primary_source: true
  - url: "https://platform.claude.com/docs/en/test-and-evaluate/develop-tests"
    title: "Anthropic — Define success criteria and build evaluations (Claude API docs)"
    date: 2026-05-10
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/evals"
    title: "OpenAI — Evals (developer docs)"
    date: 2026-05-10
    primary_source: true
  - url: "https://crfm.stanford.edu/helm/"
    title: "Stanford CRFM — Holistic Evaluation of Language Models (HELM)"
    date: 2026-05-10
    primary_source: true
rubric:
  - text: "Names evals as a multi-method choice (human eval / LLM-as-judge / golden-set / live A/B or live signal) AND treats the answer as a layered strategy, not a single-method pick — including the recognition that no single method answers the question alone."
  - text: "Identifies a concrete failure mode of choosing one eval method exclusively — e.g., golden-set passes that miss unknown regressions; LLM-as-judge agreeing with a model it shares blind spots with; CSAT that lags real failures; human eval that is too small-N and slow to gate on."
  - text: "Explains the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., a golden set only tests known categories so unknown regressions go uncaught; LLM-as-judge shares the evaluated model's blind spots, so a regression that looks fine to the model is judged fine by it; CSAT lags because users don't survey when they bounce; human eval is small-N because it doesn't scale to the volume a gate needs."
  - text: "Distinguishes which method is load-bearing in which regime — golden-set for CI gates and migration sanity checks; LLM-as-judge for scaled breadth-checking; human eval for ambiguous-quality and edge cases pre-launch; live A/B or live signal (escalation rate, retry rate, conversation length) for shipped-feature decisions and lagging metrics."
---

# Evals

## Trade-off framing

- **When this matters:** any moment you need to know whether an
  LLM feature is working — pre-launch confidence, post-migration
  sanity check, prompt-update validation, vendor comparison,
  shipped-feature regression. The PM-visible question isn't
  *"do we have evals?"* — that's binary. It's *which* evals, in
  *what order*, catching *which failure shapes*. Treating the
  question as binary ships features with one method's worth of
  signal where you needed two or three methods' worth.

- **When this breaks:** when the team picks the cheapest method
  that *feels* rigorous and stops. Four failure shapes recur:
  (1) trusting golden-set pass-rates on regressions in
  categories the golden-set doesn't cover; (2) using LLM-as-
  judge on its own without accounting for shared blind spots
  with the model being graded; (3) waiting for CSAT or other
  lagging metrics to confirm a regression that's already
  visible in live signals; (4) running only live A/B and
  exposing users to the failure mode you should have caught
  offline.

- **What it costs:** the discipline to name four methods and
  pick a layered strategy across them; the willingness to roll
  back or hold a launch when offline methods disagree;
  budget for sampled human eval on the load-bearing edge cases;
  and the patience to expand the golden-set every time a
  regression slips through, so the next migration catches the
  same category deterministically.

## 90-second bite

**Evals** are how you know whether your LLM feature is working
before users tell you it isn't. The trap is treating *"eval"* as
a single thing — *"do we have evals?"* is the wrong question.
The right one is *"which evals, in what order, catching which
failures?"*

Four methods, each load-bearing in a different regime:

1. **Human eval.** Hand-graded outputs by people who know what
   good looks like. Gold standard for ambiguous quality.
   Expensive, slow, small N. The thing you reach for when you
   need to know *"is this actually good?"* and can spend a
   week answering.

2. **LLM-as-judge.** Cheap, fast, scales to thousands of items.
   Biased toward LLM-style outputs and shares blind spots with
   the model it's grading — if Sonnet 4.6 produces a
   confidently-wrong answer, Sonnet-as-judge will often grade
   it as confidently right. The workhorse when you need
   *breadth* and can accept *acceptable signal* over
   *certainty*.

3. **Golden-set.** Curated input → expected-output pairs you
   check on every change. Deterministic, version-controlled,
   catches *known* regressions. Blind to unknown unknowns.
   The CI gate.

4. **Live A/B (and live signal).** Real users on actual traffic
   — escalation rate, retry rate, conversation length,
   downstream conversion. Catches what every other method
   misses. Slow to read out and requires exposing users to the
   failure you're trying to detect. The final check before a
   shipped change is real.

PM trap: picking the cheapest method that *feels* rigorous and
stopping. Almost every consequential eval question — *did the
migration regress quality? is the new prompt better? is this
vendor good enough?* — needs at least two methods, often three.

Eval discipline isn't *"do we have one"* — it's *"what's the
layered strategy, and which method catches which failure
shape?"* If you can only answer with one method, you can only
catch one kind of failure.

## Depth

The eval question shifts as features mature; the methods stay
roughly fixed. Across surfaces, eval discipline maps to four
methods with stable trade-offs — what changes is which method
is *load-bearing* for the question you're actually trying to
answer.

**Three measurements PMs should ask their eng team for, by
name.** Not *"how good is it?"* but:

- **Per-method coverage.** What fraction of the surface is your
  golden-set actually exercising? An 80%-pass rate on a 20-pair
  golden set tells you less than a 90%-pass rate on a 200-pair
  golden set, and the difference matters when the CEO asks
  *"can we ship?"*
- **Inter-rater agreement (for human eval) or judge-model
  agreement (for LLM-as-judge).** A single grader's verdict is
  a data point; two graders disagreeing 30% of the time is the
  *system telling you the criteria are ambiguous.*
- **Live-signal lag.** How long after a deploy will user
  behavior reflect a regression? CSAT typically lags by 3–10
  days; escalation rate by hours-to-a-day; retry rate by
  minutes. The lag shapes which method is even available
  within your decision window.

**Human eval — gold standard, expensive.** The method you reach
for when ambiguity is the load-bearing problem. Costs roughly
$10–50/hour per evaluator (the spread reflects domain
expertise; specialist medical/legal review is the upper end).
Throughput: ~20–50 items per evaluator-hour for typical
chat-quality grading. Realistic scale: hundreds-to-low-thousands
of items per pre-launch eval. Use case: *"is the output
actually good?"* on a representative sample, especially for
edge cases or new launches.

**LLM-as-judge — cheap, fast, biased.** The method you reach
for when you need scale. Per-grade cost is ~$0.005–0.02
depending on judge model and rubric length. Throughput:
thousands of items per hour, trivially. The load-bearing
caveat: *the judge shares blind spots with the model it's
grading.* Zheng et al. (2023, *Judging LLM-as-a-Judge*)
documented that strong judge models exhibit systematic biases
— position bias (preferring the first answer shown), verbosity
bias (preferring longer answers), self-enhancement bias
(preferring outputs from the same family as the judge). For
migrations between models in the same family (Sonnet 3.5 →
Sonnet 4.6), the bias is most acute: a regression that looks
reasonable to Sonnet-4.6-as-output will often look reasonable
to Sonnet-4.6-as-judge.

**Golden-set — deterministic, brittle.** The CI gate.
Hand-curated input → expected-output pairs you check on every
prompt/model change. Strength: catches *known* regressions
deterministically; perfect for migration sanity checks.
Weakness: blind to unknown unknowns by definition — a model
that gets worse on a category you didn't think to write a pair
for will pass your golden set with flying colors. Realistic
scale: 20–200 pairs for v1 features, growing as you discover
new failure shapes.

**Live A/B and live signal — slow, definitive.** The final
check. A/B tests give comparative read-outs on real traffic;
live signals (escalation rate, retry rate, conversation
length, downstream-conversion) give one-armed signals on
production. Strength: catches what every offline method misses
— preference, second-order behavior, downstream metrics that
don't appear in any golden set. Weakness: requires exposing
users to the failure you're trying to detect;
statistical-significance read-out windows are days-to-weeks
for typical SaaS traffic.

**The layering pattern.** Production eval discipline almost
always uses three or four methods in sequence: golden-set
blocks regressions in CI; LLM-judge runs over a wider
validation set after CI passes; human eval samples the cases
where LLM-judge confidence is low or stakes are high; live A/B
confirms shipped changes against real traffic. *Picking one
method and stopping* is the eval failure shape that breaks
shipped features.

**Cross-method caveat.** Eval costs don't compose linearly. A
200-pair golden set costs ~$1 to run via LLM-judge but ~$50 in
human eval — and the answers can disagree. Don't assume the
cheap method's answer is wrong because the expensive method
disagreed; sometimes the human-eval rubric is the ambiguous
one. Treat method-disagreement as a signal that the *eval
criteria themselves* need tightening.

## Decision prompt

Your team has been shipping a customer-support chatbot for 3
months. Quality has been roughly OK based on user-satisfaction
surveys (CSAT 4.2/5, stable). Two days ago the eng team
migrated the chatbot from Claude Sonnet 3.5 to Sonnet 4.6 to
get access to the 1M context window for longer support history.

Forty-eight hours after the migration: support-escalation rate
is **up 12%** week-over-week, but **CSAT is unchanged at
4.2/5**. Eng says the model is working — golden-set test suite
passes 98% (same as before migration). CSAT lags by ~5 days so
the survey hasn't caught up.

The CEO wants to know: **is the migration safe, or do we roll
back?** You have 48 hours to answer.

How do you scope the eval question? Which methods would you
run, in what order, and what would each one tell you that the
others can't? Be specific about which method catches which
failure shape, and where you'd be willing to be wrong.
