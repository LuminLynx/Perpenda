---
id: vector-search-rag-bundle-0
slug: vector-search-rag
path_id: llm-systems-for-pms
position: 10
prereq_unit_ids:
  - customization-trilemma-bundle-0
status: published
definition: Vector search and RAG quality decompose into three independent dimensions — recall (did retrieval find the right chunks?), groundedness (did the model anchor on them?), and citation faithfulness (does the cited source actually say what's claimed?) — and a RAG feature can fail on any one of them while the other two look fine.
calibration_tags:
  - claim: "RAG quality decomposes into three independent dimensions — recall (retrieval), groundedness (anchoring), and citation faithfulness (source-quote validation) — each measurable independently and each able to fail while the other two look fine."
    tier: settled
  - claim: "Recall@K and MRR are the canonical retrieval-side metrics; groundedness has converged on RAGAS-style faithfulness scoring and answer-context alignment; citation faithfulness is measured by span-level source-quote validation rather than chunk overlap."
    tier: settled
  - claim: "Hybrid retrieval (vector + BM25) outperforms either alone on most production corpora, and re-ranking on top of hybrid usually adds further marginal gain — both patterns are now standard across major providers."
    tier: settled
  - claim: "The re-ranking cost vs. recall@K threshold above 0.85 is contested across published RAG ablations — at high baseline recall the marginal lift from a re-ranker doesn't always justify its added latency and per-query cost."
    tier: contested
  - claim: "Whether long-context models will eventually subsume RAG for many enterprise knowledge-base use cases — by letting the model attend over the entire corpus instead of a retrieved slice — is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2005.11401"
    title: "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks (Lewis, Perez, Piktus, Petroni, Karpukhin, Goyal, Küttler, Lewis, Yih, Rocktäschel, Riedel, Kiela)"
    date: 2020-05-22
    primary_source: true
  - url: "https://arxiv.org/abs/2004.04906"
    title: "Dense Passage Retrieval for Open-Domain Question Answering (Karpukhin, Oğuz, Min, Lewis, Wu, Edunov, Chen, Yih)"
    date: 2020-04-10
    primary_source: true
  - url: "https://arxiv.org/abs/2309.15217"
    title: "Ragas: Automated Evaluation of Retrieval Augmented Generation (Es, James, Espinosa-Anke, Schockaert)"
    date: 2023-09-26
    primary_source: true
  - url: "https://www.anthropic.com/news/contextual-retrieval"
    title: "Anthropic — Introducing Contextual Retrieval (Contextual Embeddings + Contextual BM25)"
    date: 2026-05-15
    primary_source: true
  - url: "https://docs.cloud.google.com/vertex-ai/generative-ai/docs/grounding/overview"
    title: "Google Cloud — Vertex AI grounding overview (vendor framing of the groundedness dimension)"
    date: 2026-05-15
    primary_source: true
  - url: "https://platform.openai.com/docs/guides/retrieval"
    title: "OpenAI — Retrieval guide (developer docs)"
    date: 2026-05-15
    primary_source: true
rubric:
  - text: "Names all three independent quality dimensions — recall (did retrieval find the right chunks?), groundedness (did the model anchor on the retrieved context?), and citation faithfulness (does the cited source actually say what's claimed?) — and treats them as separately measurable rather than as one collapsed 'is the RAG good?' question."
  - text: "Explains the mechanism behind a failure on at least one of the three dimensions — e.g., recall failure as chunks not being retrieved (embedding mismatch, chunking boundary, k too low); groundedness failure as the model not anchoring on the retrieved context (chunk dilution from large k, conflicting signals between context and parametric memory); citation-faithfulness failure as the cited passage not actually supporting the claim (chunk overlap mistaken for source-quote validation)."
  - text: "Identifies that the three failure modes require different fixes — recall fails are pipeline-side (chunking, embedding, K, hybrid retrieval, re-ranking); groundedness fails are prompt-side (explicit anchoring, lower K, structured output); citation-faithfulness fails are audit-side (span-level validators, citation-coverage checks) — and treats them as non-substitutable interventions, not a single 'improve the RAG' lever."
  - text: "Names a concrete signal per dimension that distinguishes them — e.g., recall@K or MRR for recall; RAGAS-style faithfulness or answer-context alignment for groundedness; span-level source-quote validation for citation faithfulness — AND recognizes the common PM error of treating any single signal (most often recall@K, sometimes 'the answer cites a source') as a sufficient proxy for overall RAG quality."
---

# Vector search & RAG fundamentals

## Trade-off framing

- **When this matters:** any feature where the LLM is answering from
  a knowledge source it didn't see in training — internal docs, a
  product catalog, a customer's account state, regulatory text,
  recent news. Unit 10 sits immediately after Unit 9 (the
  customization trilemma) because once you've decided RAG is the
  right lever for a knowledge gap, the question becomes *"what does
  good RAG look like, and how do we tell when it isn't?"* The
  PM-visible question isn't *"is our RAG working?"* (too coarse) but
  *"on which of the three dimensions is it failing?"*

- **When this breaks:** when the team treats RAG quality as a single
  number — usually recall@K, occasionally "the answer cites a
  source" — and ships features that pass that single number while
  failing one of the other two dimensions. Three failure modes
  recur: (1) **recall failure** — the right chunks aren't in
  context, so the model confabulates around what it has;
  (2) **groundedness failure** — the right chunks are in context
  but the model doesn't anchor on them, drifting back to parametric
  memory; (3) **citation faithfulness failure** — the model cites
  a source but the cited passage doesn't actually support the
  claim, so a confident-looking citation hides a hallucination.

- **What it costs:** the discipline to measure each dimension with
  its own signal rather than collapsing them; the willingness to
  hold groundedness and citation faithfulness as separate auditable
  properties rather than assuming "we have citations therefore
  we're grounded"; and the literacy to read RAG quality reports
  with three columns instead of one.

## 90-second bite

RAG quality isn't one number. It's three independent dimensions, and
your feature can fail on any one of them while the other two look
fine.

1. **Recall — did retrieval find the right chunks?** This is the
   pipeline question. Embed the question, search the index, pull
   the top K. If the right chunks aren't in the K you retrieved,
   nothing downstream can save you. Measured with **recall@K** and
   **MRR**. Failure mode: embedding mismatch, bad chunking
   boundary, K too low.

2. **Groundedness — did the model use them?** Retrieved chunks
   land in the context window, but the model still has to decide
   how to use them. It can ignore them and fall back to parametric
   memory. It can dilute them across too many irrelevant chunks
   (high K hurts here). Measured with RAGAS-style **faithfulness**
   scores or answer-context alignment. Failure mode: model drifts
   from context, especially when retrieved chunks conflict with
   each other or with the model's prior.

3. **Citation faithfulness — does the cited source actually say
   what's claimed?** This is the strictest dimension and the one
   PMs underweight most. A citation is a UI affordance, not a
   correctness guarantee. The model can cite chunk #3 and write a
   sentence that chunk #3 doesn't support. Measured with
   **span-level source-quote validation** — extract the cited
   span, check whether it entails the claim.

The PM trap is treating any single signal as a proxy for all three.
Recall@K of 0.91 means retrieval is working, not that the feature
is good. A cited source means the model produced a citation, not
that the citation is faithful. Three dimensions, three signals,
three failure modes — diagnose which one is failing before reaching
for a fix.

## Depth

RAG quality has converged, over the period 2020–2026, on a
three-dimensional view that maps cleanly to where in the pipeline a
failure can occur. Each dimension has its own measurement
discipline and its own set of fixes; conflating them is the most
common PM error in RAG-quality reviews.

**Dimension 1: recall.** The retrieval pipeline is "embed the query,
search the index, return top K." A recall failure means the
right chunk isn't in the top K — the downstream model never sees
it. Lewis et al. (2020), *Retrieval-Augmented Generation for
Knowledge-Intensive NLP Tasks*, is the canonical reference for the
overall pattern; Karpukhin et al. (2020), *Dense Passage Retrieval*,
established the modern bi-encoder approach to dense retrieval.
**Canonical metrics:** **recall@K** (fraction of queries where the
right chunk lands in the top K results) and **MRR** (mean
reciprocal rank — how high up the right chunk appears, on average).
**Common failure modes:** (a) embedding mismatch — the query and
the chunk use different vocabulary and the embedding model doesn't
bridge them; (b) chunking boundaries — the answer spans the boundary
between two chunks and neither is a strong individual match;
(c) K too low — the right chunk is at rank 12, you retrieved 5.
**Modern production fixes:** **hybrid retrieval** (vector + BM25 in
combination) usually beats either alone — BM25 catches keyword
matches the embedding model misses; **re-ranking** with a
cross-encoder over the top 50 retrieved candidates can recover
recall at smaller K; Anthropic's **Contextual Retrieval** technique
prepends a per-chunk context summary before embedding, addressing
chunk-boundary loss explicitly.

**Dimension 2: groundedness.** Retrieved chunks land in the context
window, but the model still has to decide whether to use them.
Groundedness is the property of the generated answer being
*anchored on* the retrieved context rather than the model's
parametric memory. **Canonical metric:** RAGAS (Es et al., 2023)
faithfulness — decompose the answer into atomic claims, check
whether each is entailed by the retrieved context; alternative
phrasing is answer-context alignment. **Common failure modes:**
(a) **chunk dilution** — retrieve K=20 chunks where only 3 are
relevant; the model's attention spreads and it falls back on
priors; (b) **conflict between context and parametric memory** —
the retrieved chunk says X, the model "knows" Y from training, and
in a non-trivial fraction of cases the model goes with Y; (c)
**system-prompt under-specification** — the prompt doesn't tell
the model that the retrieved context is authoritative, so it
treats it as a hint rather than ground truth. **Fixes are mostly
on the prompting side** — explicit anchoring instructions ("answer
only from the provided context"), structured output that forces
citation back to retrieved chunks, lower K with stronger
re-ranking. Groundedness is the dimension where most PMs feel
"the RAG is wrong" but recall@K reports look fine.

**Dimension 3: citation faithfulness.** This is the strictest
dimension and the one PMs underweight most. A citation in the
generated answer is a UI affordance — the model produced a
reference. It is *not* a correctness guarantee. The model can cite
chunk #3 and write a sentence that chunk #3 does not actually
support. **Canonical measurement:** **span-level source-quote
validation** — extract the specific span of the cited source,
check whether that span entails the specific claim. This is
distinct from groundedness (which asks whether the answer is
anchored on the retrieved context as a whole) and from recall
(which asks whether the right chunk was retrieved at all). A RAG
system can have high recall, high groundedness, and *still* fail
citation faithfulness if the cited spans don't align with the
specific sentences they're attached to. **Common failure modes:**
(a) **chunk overlap mistaken for entailment** — the cited chunk
mentions the topic but doesn't support the specific claim; (b)
**aggregate citations** — the model emits one citation covering a
multi-claim paragraph; some claims are supported, some aren't;
(c) **stale chunks** — the cited source has been updated and no
longer says what the model claims it says (an indexing-freshness
problem the citation surface hides).

**Why three dimensions matters for PM diagnosis.** When a
stakeholder reports "the RAG is wrong," the productive first
question is *which dimension is failing?* — not *what fix should we
apply?* Recall failures show as the model not knowing things that
are clearly in the docs; groundedness failures show as the model
saying things that contradict the docs it just retrieved;
faithfulness failures show as cited answers that look correct
until someone reads the cited passage. Each requires a different
intervention. Reaching for "increase chunk size" or "add a
re-ranker" before identifying which dimension is failing is the
RAG equivalent of fine-tuning a knowledge gap (Unit 9): a real
investment in the wrong direction.

**Vendor framing.** Provider docs frame the dimensions slightly
differently — Anthropic's Contextual Retrieval post leads with
recall improvements; Google's Vertex AI documentation centers on
"grounding" as the umbrella term (closer to dimensions 2 + 3
combined); OpenAI's Retrieval guide is implementation-focused.
**Read multiple framings.** The three-dimension view is the most
useful PM-facing decomposition; vendor framings tend to emphasize
whichever dimension their tooling currently leads on.

## Decision prompt

Your team is about to ship a RAG-powered feature that answers
customer questions from your internal knowledge base. Before
launch, you need to define what *"quality"* means for this feature
so that the eng team and the post-launch on-call rotation have a
shared model.

Name the three independent quality dimensions you'd track, what
each tells you, and one concrete signal you'd use for each. Then
describe the failure that each signal would *miss* — the kind of
problem you'd need a different signal to catch — so the team
understands why one number isn't enough. Be specific about what
"good" looks like per dimension and where you'd push back if eng
proposes a single composite "RAG quality" score.
