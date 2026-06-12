---
id: safety-bundle-0
slug: safety
path_id: llm-systems-for-pms
position: 15
prereq_unit_ids:
  - agents-bundle-0
status: published
definition: Safety and content moderation is the decision of how to bound a feature's abuse surface — what to moderate, how strict to set the threshold, and where the human sits — three coupled choices whose combination determines whether the feature is safe and usable or safe but useless.
calibration_tags:
  - claim: "All major providers ship a moderation or safety-filter layer (OpenAI Moderation, Anthropic usage-policy enforcement, Google/Vertex safety filters) that classifies content against harm categories with a tunable threshold."
    tier: settled
  - claim: "Moderation is a precision/recall classification problem: raising the threshold to catch more harmful content (recall) lowers precision and over-refuses legitimate use, and the two move against each other."
    tier: settled
  - claim: "Input-side and output-side moderation catch different failure classes — input moderation catches prompt-injection and abuse attempts before a generation, output moderation catches harmful content the model produces from benign-looking prompts — so layered (both-sides) defense is the production standard for real abuse surfaces."
    tier: settled
  - claim: "Whether a dedicated safety classifier (Llama Guard-style) outperforms a general LLM-as-judge moderation call is task- and category-specific and not generalizable from published benchmarks."
    tier: contested
  - claim: "The right threshold and human-review locus are domain-specific — set by the feature's cost-of-one-incident and abuse surface, not by a provider default — and there is no generalizable optimum."
    tier: contested
  - claim: "Whether next-generation models' built-in safety training will reduce the need for an external moderation layer for most product features is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2208.03274"
    title: "A Holistic Approach to Undesired Content Detection in the Real World (Markov, Zhang, Agarwal, Eloundou, Lee, Adler, Jiang, Weng — OpenAI, the moderation-classifier reference)"
    date: 2022-08-05
    primary_source: true
  - url: "https://arxiv.org/abs/2312.06674"
    title: "Llama Guard: LLM-based Input-Output Safeguard for Human-AI Conversations (Inan, Upasani, Chi, Rungta, Iyer, Mao, Tontchev, Hu, Fuller, Testuggine, Khabsa — Meta)"
    date: 2023-12-07
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/moderation"
    title: "OpenAI — Moderation guide (input/output moderation, categories, thresholds)"
    date: 2026-05-23
    primary_source: true
  - url: "https://ai.google.dev/gemini-api/docs/safety-settings"
    title: "Google — Gemini API safety settings (per-category tunable thresholds)"
    date: 2026-05-23
    primary_source: true
  - url: "https://www.nist.gov/itl/ai-risk-management-framework"
    title: "NIST — AI Risk Management Framework (AI RMF 1.0; threat-modeling / proportionate-defense framing)"
    date: 2023-01-26
    primary_source: true
  - url: "https://owasp.org/www-project-top-10-for-large-language-model-applications/"
    title: "OWASP — Top 10 for Large Language Model Applications (abuse-surface taxonomy, incl. prompt injection)"
    date: 2026-05-23
    primary_source: true
rubric:
  - text: "Names the three coupled safety decisions — what to moderate (input prompts / model output / both), how strict to set the threshold (the over-refusal vs. under-block precision/recall trade), and where the human sits (auto-block / flag-for-review / human-in-the-loop on high-risk actions) — AND treats them as coupled, where the combination produces safe-and-usable vs. safe-but-useless vs. unsafe, rather than as three independent toggles."
  - text: "Explains the mechanism behind a mismatched-combination failure — why it happens, not just that it happens — e.g., a strict threshold with auto-block and no human over-refuses legitimate use because precision falls as recall rises, so the feature becomes safe-but-useless; or a lenient threshold that moderates only the output lets a prompt-injection or jailbreak through because the abuse entered at the input, which was never inspected."
  - text: "Identifies that the three decisions constrain each other — they cannot be chosen independently; e.g., auto-block with no human in the loop forces a stricter threshold because every error executes unreviewed, and moderating only one side forecloses catching the abuse class that enters on the other side — so a coherent design is a combination whose legs reinforce each other, not three toggles set in isolation."
  - text: "Maps a matched combination to an abuse surface by its stakes — names at least one coherent triple (high-risk action surface: moderate both + a recall-tuned threshold + human-in-the-loop on high-risk actions; low-stakes assistant: moderate output + a balanced threshold + flag-for-review; high-volume low-stakes: moderate output + a precision-tuned threshold + auto-block) and why the abuse surface drives the choice — AND names the common PM error of treating safety as a single content-filter toggle bolted on at launch instead of threat-modeling the feature's specific abuse surface and layering proportionate defenses."
---

# Safety + content moderation

## Trade-off framing

- **When this matters:** any feature that takes untrusted input from
  users or produces output users act on — which, once the feature can
  also *act* through tools (Unit 12) or run a multi-step loop (Unit
  14), means the blast radius is no longer just a bad sentence but a
  bad *action*. Unit 15 closes the Production block because it governs
  the abuse surface every prior Production unit builds: tool use
  creates the action surface, agents are its maximal extent, and
  safety is the discipline that bounds both. The PM-visible question
  is not "do we have a content filter" (binary, and the answer is
  always "add one") but "what is this feature's specific abuse
  surface, and which combination of defenses is proportionate to it."

- **The three coupled decisions, and the combinations that work:**
  safety is not one toggle. It is three. **What to moderate** is
  whether you inspect the user's input, the model's output, or both.
  **How strict to set the threshold** is where you sit on the
  over-refusal versus under-block trade — a stricter threshold catches
  more harm but blocks more legitimate use, a looser one preserves
  use but lets more harm through. **Where the human sits** is whether
  a flagged item is auto-blocked, flagged for asynchronous review, or
  routed to a human-in-the-loop before a high-risk action executes.
  The decisions are coupled — the combination, not any single choice,
  decides whether the feature is safe and usable. Three combinations
  that match:
  - *Moderate both, a recall-tuned threshold, and human-in-the-loop
    on high-risk actions* — the **guarded-action surface**: right when
    the feature can take consequential or irreversible actions, where
    a missed harm is expensive and a human can absorb the
    over-refusals the strict threshold produces.
  - *Moderate output, a balanced threshold, and flag-for-review* — the
    **assistive surface**: right for a general assistant where most
    interactions are benign, asynchronous review catches the misses,
    and over-refusal would quietly kill adoption.
  - *Moderate output, a precision-tuned threshold, and auto-block* —
    the **high-volume low-stakes surface**: right when volume makes a
    human impossible and the cost of one missed item is low, so you
    accept some leakage to avoid over-refusing at scale.

- **When this breaks:** a *mismatched* combination — the right pieces,
  wrong pairing for the abuse surface. Two failure poles recur: (1)
  **safe-but-useless** — a strict threshold with auto-block and no
  human on a surface where legitimate use looks superficially risky,
  so the feature over-refuses real users into abandonment; (2)
  **unsafe** — a lenient threshold that moderates only the output on a
  surface exposed to prompt injection, so a crafted input steers the
  model and the harm was never inspected where it entered. The
  decision teams most often skip is threat-modeling the abuse surface
  *before* picking defenses, because the launch demo never includes an
  adversary.

- **What it costs:** the discipline to threat-model the specific abuse
  surface rather than reach for a default filter; the engineering to
  run moderation on both sides and a real human-review path where the
  stakes require it; and the measurement cost of treating moderation
  as a precision/recall problem you tune on representative adversarial
  input, not a toggle you switch on.

## 90-second bite

Legal says the feature needs a content filter before launch, and eng
says they will run the output through a moderation API and block what
it flags. That is the trap: "add a content filter" looks like one
decision and is actually three coupled ones, and the combination is
what decides whether the feature ships safe-and-usable or
safe-but-useless.

**What to moderate:** the user's input, the model's output, or both.
**How strict the threshold:** where you sit on the over-refusal versus
under-block trade. **Where the human sits:** auto-block, flag for
review, or a human in the loop before a high-risk action. No single
choice is safe on its own. The combination sets the outcome, and there
are three coherent ones:

1. **Guarded-action surface** — moderate both, tune the threshold for
   recall, and put a human in the loop on high-risk actions. Right
   when the feature can take consequential or irreversible actions,
   where a missed harm is expensive and a human can absorb the
   over-refusals a strict threshold produces.

2. **Assistive surface** — moderate output, run a balanced threshold,
   and flag for review. Right for a general assistant where most use
   is benign, asynchronous review catches the misses, and over-refusal
   would quietly kill adoption.

3. **High-volume low-stakes surface** — moderate output, tune the
   threshold for precision, auto-block. Right when volume rules out a
   human and the cost of one missed item is low, so you accept a
   little leakage to avoid over-refusing at scale.

The failure is not a bad option. It is a mismatched combination. A
strict threshold with auto-block and no human turns a feature
safe-but-useless: precision falls as recall rises, so it refuses real
users until they leave. A lenient threshold that watches only the
output is unsafe on any surface exposed to prompt injection, because
the crafted input that steers the model was never inspected where it
entered.

The PM call is to threat-model this feature's specific abuse surface,
match the combination to it, and force the threshold and human-locus
decisions before launch — not after the first incident.

## Depth

Safety is where an LLM feature stops being only a quality problem and
becomes a problem about adversaries and consequences. Hallucination
(Unit 7) taught containment as one axis of reliability — making a
residual *wrong* answer safe. Unit 15 widens "failure" from *wrong* to
*harmful or abusive*, and treats containment as a first-class,
threat-modeled discipline rather than a reliability sub-axis. The
shift that matters for a PM is that "add a content filter" hides three
independent decisions whose combination, not whose individual choices,
determines whether the feature is operable.

**What to moderate.** Moderation can sit on the input, the output, or
both. Input moderation inspects the user's prompt before generation —
it is where you catch prompt injection, jailbreak attempts, and
policy-violating requests before they cost a token or steer a tool
call. Output moderation inspects what the model produced — it is where
you catch harmful content the model generates even from a
benign-looking prompt. The two catch different failure classes, which
is why both-sides moderation is the production standard for a real
abuse surface: input-only misses the model's own harmful generations,
and output-only misses the injected instruction that has already
redirected the model (and, on a tool-using feature, may already have
triggered an action). Llama Guard (Inan et al., 2023) is built around
exactly this input-output framing; OpenAI's moderation guide and
Google's per-category safety settings expose both hooks.

**How strict to set the threshold.** A moderation classifier returns a
score per harm category; the threshold is where you cut. This is a
precision/recall trade and the two move against each other. Tune for
**recall** (catch more harm) and precision falls — you block more
legitimate content, the over-refusal failure. Tune for **precision**
(block only clear harm) and recall falls — more harmful content slips
through, the under-block failure. There is no universally right
threshold; it is set by the feature's cost-of-one-incident and the
base rate of abuse on its surface. The OpenAI moderation work (Markov
et al., 2022) frames undesired-content detection as exactly this
real-world classification problem, where the operating point is a
deployment decision, not a model property.

**Where the human sits.** A flagged item can be auto-blocked
(deterministic, no human, instant), flagged for asynchronous review (a
human sees it after the fact, the item still went through or was
queued), or routed to a human in the loop before a high-risk action
executes (a human approves before anything irreversible happens).
Where the human sits is what lets you live with an imperfect
threshold: a human reviewing flags lets you run a looser threshold
because the misses get caught downstream, while auto-block with no
human means the threshold is the entire defense and must be stricter.

**Why the combination is load-bearing.** The three matched
combinations — guarded-action (moderate both, recall-tuned threshold,
human-in-the-loop), assistive (moderate output, balanced threshold,
flag-for-review), and high-volume low-stakes (moderate output,
precision-tuned threshold, auto-block) — cohere because each decision
reinforces the others. Auto-block with no human *forces* a stricter
threshold, because every classifier error executes unreviewed, with no
second line to catch a miss or reverse an over-block. A human in the
loop *permits* a looser threshold, because the human is the recall
backstop. And moderating only one side forecloses the abuse class that
enters on the other: you cannot put a human in the loop on an injected
tool call you never inspected at the input. Pick auto-block and you
have implicitly committed to a strict threshold; pick output-only and
you have implicitly conceded the input-side abuse surface. The choices
are not independent.

**Threat-model first, then layer proportionate defenses.** The PM
discipline is to characterize the abuse surface before picking
defenses — who the adversary is, what they would try, and what one
successful abuse costs. The OWASP Top 10 for LLM Applications is a
working taxonomy of that surface (prompt injection, insecure output
handling, excessive agency, and so on), and the NIST AI Risk
Management Framework is the canonical framing for proportionate,
documented defense. A feature that can only emit text has a small
blast radius; a feature that can call your API (Unit 12) or run an
agent loop (Unit 14) has a large one, and the safety design has to be
proportionate to that surface, not to a checkbox. The recurring PM
error is treating safety as a single content-filter toggle bolted on
at launch — one moderation call on the output, threshold at the
vendor default — instead of threat-modeling the surface and layering
input plus output moderation, a tuned threshold, and a human locus
matched to the stakes.

**Measurements PMs should ask for by name.** Not "is it safe" but:
**false-refusal rate** on a representative set of *legitimate* inputs
— the over-refusal cost, the number that tells you whether the
threshold is quietly killing adoption; **catch rate (recall) on a
red-team set** of adversarial inputs, including prompt-injection
attempts, not just obviously-harmful prompts — the under-block cost;
and **review-queue volume and latency** if a human is in the loop —
the number that tells you whether the human locus is staffable or
theoretical. A safety design without these three is a toggle, not a
discipline.

**Vendor framing.** Providers ship moderation as a feature and frame
it as close to a toggle, with default thresholds and category lists.
The defaults are a starting point, not a deployment decision — they do
not know your abuse surface or your cost-of-one-incident. Read the
provider docs for the hooks (input and output, per-category
thresholds) but set the operating point against your own red-team set,
and treat the dedicated-classifier versus LLM-as-judge choice as a
measurement question on your categories, not a vendor preference.

## Decision prompt

Your team is two weeks from launching a consumer-facing AI assistant
that answers users' questions and can take actions on their account —
it can look things up, update settings, and initiate a transfer
between the user's own accounts. Legal has flagged that it needs
content moderation before launch. Eng's plan: run the model's final
output through a provider moderation API at the default threshold and
block anything that comes back flagged. A prototype passes the demo
script cleanly.

Before you sign off, scope what safety actually requires here. Name
the three coupled decisions, threat-model this feature's specific
abuse surface, and say which combination you would pick and why the
surface drives that choice. Be specific about where eng's
moderate-the-output-at-the-default-threshold plan leaves the feature
exposed — and what you would want decided about the threshold and the
human's role before launch, not after the first incident.
