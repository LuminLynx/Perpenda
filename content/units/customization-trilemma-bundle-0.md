---
id: customization-trilemma-bundle-0
slug: customization-trilemma
path_id: llm-systems-for-pms
position: 9
prereq_unit_ids:
  - cost-dynamics-bundle-0
status: published
definition: The customization trilemma is the decision of which approach — fine-tuning, RAG, or prompting — to invest in when an LLM feature's baseline isn't good enough, picked by matching the approach's mechanism to the shape of the underlying quality problem rather than by approach familiarity or budget.
calibration_tags:
  - claim: "RAG (retrieval-augmented generation) is a standard pattern across major providers — embed documents, retrieve relevant chunks per query, inject into context — that adapts a general model to specific knowledge without retraining."
    tier: settled
  - claim: "Modern production fine-tuning is mostly parameter-efficient (PEFT / LoRA), training small adapter weights rather than full-parameter updates."
    tier: settled
  - claim: "Fine-tuning shifts a model's behavior distribution (defaults for tone, style, classification taste) but does not reliably 'load' factual knowledge in a way that survives training-data drift."
    tier: settled
  - claim: "The crossover point at which fine-tuning becomes cost-effective vs. continued prompt engineering plus RAG is task-and-volume-specific and not generalizable from published benchmarks."
    tier: contested
  - claim: "Whether next-generation models will reduce the need for fine-tuning across most behavior-shift use cases by improving instruction-following defaults is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2005.11401"
    title: "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks (Lewis, Perez, Piktus, Petroni, Karpukhin, Goyal, Küttler, Lewis, Yih, Rocktäschel, Riedel, Kiela)"
    date: 2020-05-22
    primary_source: true
  - url: "https://arxiv.org/abs/2106.09685"
    title: "LoRA: Low-Rank Adaptation of Large Language Models (Hu, Shen, Wallis, Allen-Zhu, Li, Wang, Wang, Chen)"
    date: 2021-06-17
    primary_source: true
  - url: "https://www.anthropic.com/news/fine-tune-claude-3-haiku"
    title: "Anthropic — Fine-tune Claude 3 Haiku in Amazon Bedrock (current Anthropic fine-tuning surface; native API fine-tuning not yet generally available)"
    date: 2026-05-14
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/supervised-fine-tuning"
    title: "OpenAI — Supervised fine-tuning (API docs)"
    date: 2026-05-14
    primary_source: true
  - url: "https://docs.cloud.google.com/vertex-ai/generative-ai/docs/models/gemini-supervised-tuning"
    title: "Google — About supervised fine-tuning for Gemini models (Vertex AI docs; explicit confirmation that Gemini tuning uses LoRA under the hood)"
    date: 2026-05-14
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/optimizing-llm-accuracy"
    title: "OpenAI — Optimizing LLM Accuracy (developer docs; vendor framing of the same trilemma)"
    date: 2026-05-14
    primary_source: true
rubric:
  - text: "Names the customization trilemma the unit is built around — fine-tuning vs. RAG vs. prompting as three approaches that map to three different problem shapes (behavior shift / knowledge gap / spec clarity), not as interchangeable levers — AND treats diagnosing the failure mode as the load-bearing step before picking an approach."
  - text: "Identifies a concrete failure mode of mis-matching approach to problem shape — e.g., fine-tuning a knowledge gap, RAG-ing a behavior issue, or prompting a knowledge gap."
  - text: "Names the mechanism behind the named failure mode — why it happens, not just that it happens — e.g., fine-tuning a knowledge gap fails because training shifts behavior distribution rather than loading facts (so facts go stale and the training cost doesn't amortize); RAG-ing a behavior issue fails because retrieval injects content into context but doesn't change how the model interprets it (so the tone shift you wanted doesn't happen); prompting a knowledge gap fails because no amount of instruction retrieves information the model doesn't have (so the gap surfaces as hallucination instead of refusal)."
  - text: "Distinguishes which approach is load-bearing for which problem shape — fine-tuning for behavior / style / persona shifts where the model can do the task but defaults to the wrong shape; RAG for knowledge gaps where the answer is in retrievable source material the model wasn't trained on; prompting for spec-clarity problems where the model can do the task and the gap is in knowing what 'right' looks like — AND recognizes that production features usually layer multiple approaches (RAG + prompting is the typical default) rather than picking one, with the eng-cost / reversal-cost shape driving sequencing decisions."
---

# Fine-tuning vs. Prompting vs. RAG

## Trade-off framing

- **When this matters:** any feature where the LLM-shaped baseline
  isn't good enough and engineering is debating which lever to pull
  — *"should we fine-tune this?"*, *"should we add RAG?"*, *"can we
  just fix the prompt?"* Unit 9 is the synthesis decision *across*
  customization strategies, taught after Units 4 (you can measure),
  5 (you've already picked a tier), and 6 (you know how to prompt).
  The PM-visible question isn't *"which is best?"* (none is — they
  solve different problems) but *"what shape is the quality problem
  we're actually facing?"*

- **When this breaks:** when the team treats the three approaches as
  interchangeable levers and picks by familiarity or budget instead
  of by problem shape. Three failure modes recur: (1) fine-tuning a
  *knowledge gap* — training shifts the model's behavior distribution
  but doesn't reliably "load" facts, so the facts go stale faster
  than the training amortizes; (2) RAG-ing a *behavior issue* —
  retrieval injects content into context but doesn't change how the
  model interprets it, so the tone or persona you wanted still
  doesn't surface; (3) prompting a *knowledge gap* — no amount of
  instruction retrieves information the model doesn't have, so the
  gap becomes hallucination instead of a clean refusal.

- **What it costs:** the discipline to *diagnose the failure mode*
  before reaching for an approach; the willingness to measure on
  representative input (Unit 4) rather than vibe-check; and the
  literacy to compare across approaches that have wildly different
  cost shapes — prompting is ~$10 of token spend and zero
  engineering, RAG is mid-eng-cost (retrieval pipeline + index
  maintenance) plus a higher per-call token bill, fine-tuning is
  thousands of dollars in training spend + dataset curation + eval
  discipline plus a long reversal cycle if you regret it.

## 90-second bite

When an LLM feature isn't good enough, three approaches sit on the
table — **fine-tuning**, **RAG**, and **prompting**. The PM trap is
treating them as interchangeable levers and picking by familiarity
or budget. They're not interchangeable. Each solves a different
problem shape, and picking the wrong one for the problem you
actually have wastes both money and calendar.

Three approaches, three problem shapes:

1. **Knowledge gap → RAG.** The model can do the task but doesn't
   know the facts — your company's docs, a current customer's
   account state, a product launched after the training cutoff.
   Retrieval pulls the right information into context per query.
   RAG works the moment you turn it on (modulo retrieval quality),
   and the costs are eng-shaped (build the index, maintain it)
   plus a higher per-call token bill. The win: facts stay fresh,
   no retraining.

2. **Behavior shift → Fine-tuning.** The model can answer but the
   *shape* of the answer is wrong — tone is off-brand, persona is
   corporate-canned, classification taste doesn't match yours,
   structured outputs drift to a default style. Training on
   examples of the right behavior shifts the model's defaults.
   Expensive (thousands of dollars in training spend + dataset
   curation + eval discipline), slow to reverse, but it's the
   lever that changes the model's defaults — what it does when
   nothing in the prompt nudges otherwise.

3. **Spec clarity → Prompting.** The model can do the task and
   knows the facts, but doesn't know what *exactly* you want —
   the output schema, the rule boundaries, the format conventions.
   Cheap to iterate (~$10 of token spend, zero engineering), but
   has a ceiling. Past a certain spec complexity, prompts get
   unwieldy and examples start fighting each other.

The PM call isn't *"which approach is best?"* — none of them is.
It's *"what shape is the quality problem we're actually facing?"*
Diagnose first, pick second. Production systems usually layer two
or three of these; picking the wrong primary approach for the
wrong problem just compounds cost in the wrong direction.

## Depth

The customization decision is taught last in the first-half arc
because it presupposes everything upstream — Unit 4 (you can
measure quality), Unit 5 (you've picked a tier), Unit 6 (you know
how to prompt). Unit 9 is what you reach for when the baseline
isn't good enough and you need to invest in moving it. The
discipline is the *diagnosis step* before the approach pick.

**Three measurements PMs should ask their eng team for, by name:**

- **The eval-set delta per approach.** Run your eval (Unit 4)
  against the baseline, baseline + RAG, baseline + a fine-tune,
  and baseline + a prompt rewrite. The delta per approach reveals
  which one moves your load-bearing metric. A knowledge gap shows
  as a flat baseline that jumps with RAG; a behavior issue barely
  moves with RAG and jumps with fine-tuning. **This is the
  diagnostic test, not just a measurement.**
- **Reversal cost per approach.** Prompting: minutes (revert the
  prompt). RAG: days-to-weeks (disable the retrieval call).
  Fine-tuning: weeks-to-months (the fine-tuned model is what your
  traffic now depends on; reversal means a new training run or a
  vendor rollback). Sequence the cheap, reversible approaches
  first.
- **Dataset cost per approach.** Prompting needs zero data. RAG
  needs a clean document corpus and an embedding pipeline.
  Fine-tuning needs labeled examples — usually 50–5,000 depending
  on the behavior shift — and the labor of curating them is where
  the real cost lives.

**Knowledge gap → RAG.** The mechanics: embed documents into
vectors, index, retrieve top-K relevant chunks per query, inject
into the prompt before generation. Lewis et al. (2020),
*Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks*,
is the canonical reference. Modern production RAG adds re-ranking,
query rewriting, and hybrid (keyword + vector) retrieval on top
of the core pattern. **Load-bearing when the answer is in
retrievable source material the model wasn't trained on.**
**Failure mode:** bad retrieval is bad answers. Retrieval quality
is its own measurement discipline (recall@K, MRR); if RAG isn't
moving your eval, the question is usually *"is the right chunk
being retrieved?"* before *"is the model using it well?"*

**Behavior shift → Fine-tuning.** Modern production fine-tuning
is mostly **parameter-efficient (PEFT)** — LoRA (Hu et al., 2021)
and variants train small adapter weights instead of updating the
full model. Cheaper, faster, easier to roll back than
full-parameter. **Provider state in 2026:** OpenAI supports
supervised fine-tuning on GPT-4.1 and GPT-4.1 mini; Google offers
Gemini 2.5 Pro / Flash / Flash-Lite tuning via Vertex AI (Google's
docs confirm the tuning uses LoRA under the hood). Anthropic's
native fine-tuning API is not yet generally available — Claude 3
Haiku fine-tuning is GA only through Amazon Bedrock, which means
an Anthropic shop wanting to fine-tune today routes through AWS
rather than Anthropic directly. **The bottleneck is dataset
curation, not compute.** A fine-tune on 50 high-quality examples
often outperforms one on 5,000 noisy ones. **Load-bearing when you
need to change the model's defaults** — tone, persona,
classification taste, output style. **Failure mode:** training on
a knowledge gap. Fine-tuning shifts behavior distribution but
doesn't reliably *load* facts. The model gets training-distribution
facts right and confabulates for anything outside it; six months
later the products are different and the training is stale.

**Spec clarity → Prompting.** Covered at depth in Unit 6. Unit 9's
contribution: identify when prompting is the right tool *at all*.
If the model can do the task and knows the facts, the gap is your
specification — write it down (instructions for rules, examples
for ambiguous criteria). If the model can't do the task even with
a perfect spec, prompting won't rescue it; that's a fine-tuning or
tier question. If the model doesn't know the facts, no instruction
loads them; that's RAG.

**The hybrid pattern.** Production features usually layer two
approaches; rarely all three. The most common is **RAG + prompting**
— RAG handles knowledge, prompting handles spec — both cheap, both
reversible, both compound at scale without retraining cycles.
Fine-tuning enters when behavior shift is the residual problem
*after* RAG and prompting are tuned. Sequencing matters: cheap and
reversible first. Reversing this order — fine-tuning a knowledge
gap because *"we have budget for it"* — is the failure mode that
wastes the most money in the customization decision.

**Vendor framing caveat.** Provider docs tend to lead with the
approach the vendor has the strongest commercial position on.
OpenAI's *Optimizing LLM Accuracy* walks the trilemma roughly the
same way this unit does; Anthropic emphasizes prompting + caching
before fine-tuning; Google leads with fine-tuning via Vertex.
**Read multiple vendor framings, not one.** The trilemma is real;
the vendor positioning around it is partly real, partly commercial.

## Decision prompt

Your team ships a customer-support agent built on a Sonnet-class
model with RAG over a knowledge base of company docs. Three months
post-launch, quality issues stack up and the team is asking what to
invest in next quarter. Three concrete complaints from real users
and internal stakeholders:

1. **Tone problem.** Users tell support that the agent *"feels
   corporate / canned"* — not warm enough for the brand. The same
   exact words wouldn't bother them from a human agent; the model's
   default voice is the issue.
2. **Knowledge gap.** Two new product lines launched in the past
   6 months. The agent doesn't know about them — it either guesses
   or refuses. The product docs exist in Confluence; the agent's
   just not seeing them.
3. **Output drift.** On multi-step questions, the model sometimes
   returns a wall of text instead of the team's preferred
   numbered-list-with-action-items format. The spec for what
   *"good"* looks like is documented and clear; the model just
   doesn't follow it consistently.

The eng lead is asking which **one** approach to invest in next
quarter: *"do we fine-tune the model, expand the RAG index, or
rewrite the prompt? Pick one."*

How do you scope the decision? Walk through which approach maps to
which of the three problems, what evidence you'd want before
committing, and where the eng lead's *"pick one"* framing gets the
trade-off wrong. Be specific about cost/risk shape per approach
and where you'd be willing to be wrong.
