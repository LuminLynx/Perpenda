---
id: tool-use-bundle-0
slug: tool-use
path_id: llm-systems-for-pms
position: 12
prereq_unit_ids:
  - streaming-ux-bundle-0
status: published
definition: Tool use is the decision of how to let a model call your systems — how granular the tools are, how strictly their inputs are validated, and where errors are recovered — three coupled choices whose combination determines whether the feature is reliable and debuggable or brittle and opaque.
calibration_tags:
  - claim: "Function/tool calling is a standard capability across all major providers — define a JSON-schema'd tool, the model emits a structured call, your code executes it and returns the result."
    tier: settled
  - claim: "Provider 'strict' / structured-output modes (constrained decoding) make schema-invalid tool calls effectively impossible, moving enforcement into the decoder rather than relying on the model to conform."
    tier: settled
  - claim: "Whether many-narrow-tools or few-broad-composite-tools yields better tool-selection accuracy is task- and model-specific and not generalizable from published benchmarks."
    tier: contested
  - claim: "Whether model-side (ReAct-style) error recovery outperforms orchestrator-side recovery on production reliability is workload-dependent and contested across published agent evaluations."
    tier: contested
  - claim: "Whether models will become reliable enough at long multi-tool orchestration to make deterministic orchestrator scaffolding unnecessary is unsettled."
    tier: unsettled
sources:
  - url: "https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview"
    title: "Anthropic — Tool use with Claude (define-schema, structured call, execute, return)"
    date: 2026-05-17
    primary_source: true
  - url: "https://developers.openai.com/api/docs/guides/function-calling"
    title: "OpenAI — Function calling guide (incl. strict structured outputs)"
    date: 2026-05-17
    primary_source: true
  - url: "https://docs.cloud.google.com/vertex-ai/generative-ai/docs/multimodal/function-calling"
    title: "Google Cloud — Vertex AI function calling (function declarations)"
    date: 2026-05-17
    primary_source: true
  - url: "https://www.anthropic.com/news/building-effective-agents"
    title: "Anthropic — Building effective agents (workflows vs. agents; simplest solution first)"
    date: 2026-05-17
    primary_source: true
  - url: "https://arxiv.org/abs/2210.03629"
    title: "ReAct: Synergizing Reasoning and Acting in Language Models (Yao, Zhao, Yu, Du, Shafran, Narasimhan, Cao)"
    date: 2022-10-06
    primary_source: true
  - url: "https://arxiv.org/abs/2302.04761"
    title: "Toolformer: Language Models Can Teach Themselves to Use Tools (Schick, Dwivedi-Yu, Dessì, Raileanu, Lomeli, Zettlemoyer, Cancedda, Scialom)"
    date: 2023-02-09
    primary_source: true
rubric:
  - text: "Names the three coupled tool-use decisions — tool granularity (many narrow tools vs. few broad composites), schema strictness (strict validation vs. lenient coercion), and error-recovery locus (model-side retry vs. orchestrator-side catch-and-re-prompt) — AND treats them as coupled, where the combination sets the feel, rather than as three independent toggles."
  - text: "Explains the mechanism behind a mismatched-combination failure — e.g., broad lenient tools with orchestrator-only recovery let errors fall through silently because the orchestrator cannot anticipate the model's improvisations; or strict narrow tools with model-side recovery on a destructive call let the model retry something dangerous."
  - text: "Identifies that the three decisions constrain each other — they cannot be chosen independently; e.g., a lenient schema implies model-side recovery (the orchestrator has no validated shape to catch on), and an open-ended tool surface cannot be deterministically wrapped — so a reliable combination is one whose legs reinforce each other, not three toggles set in isolation."
  - text: "Maps a matched combination to a surface by its stakes — narrow tools plus strict schema plus orchestrator recovery for high-stakes irreversible mutations like a refund; broad lenient tools plus model-side recovery for open-ended assistant surfaces; narrow strict tools plus model-side recovery for semi-structured tasks — and explains why the stakes drive the choice AND names the common PM error of treating tool use as one decision, inheriting SDK defaults, or skipping the recovery decision because it has no happy-path demo."
---

# Tool use / function calling

## Trade-off framing

- **When this matters:** any feature where the model doesn't just
  answer but acts — calls your API, queries your database,
  triggers a workflow. This generalizes Unit 10's
  retrieval-as-a-tool into the broader pattern where the model can
  call your systems. The PM-visible question is not "can the model
  use tools" (every major provider supports it) but "what
  combination of tool design does this surface actually need."

- **The three coupled decisions, and the combinations that
  work:** tool use is not one decision. It is three. **Tool
  granularity** is whether you expose many narrow single-purpose
  tools or a few broad composites. **Schema strictness** is
  whether the tool's input contract rejects malformed arguments
  or coerces them best-effort. **Error-recovery locus** is
  whether a failed tool call is handed back to the model to
  reason about and retry, or caught by a deterministic
  orchestrator that decides retry, abort, or escalate. The
  decisions are coupled — the combination, not any single choice,
  produces the feel. Three combinations that match:
  - Narrow tools with a strict schema and orchestrator recovery
    produce the reliable-workflow feel: predictable, auditable,
    each tool does one thing, failures caught deterministically.
    Right for high-stakes surfaces such as payments or data
    mutations, where correctness and auditability beat
    flexibility.
  - Broad tools with a lenient schema and model-side recovery
    produce the agentic-exploration feel: the model improvises
    and self-heals across novel requests. Right for open-ended
    assistant surfaces where range beats predictability.
  - Narrow tools with a strict schema and model-side recovery
    produce the guided-agent feel: a constrained surface, but
    the model recovers from its own argument mistakes. Right for
    semi-structured tasks between the two extremes.

- **When this breaks:** a mismatched combination — the right
  pieces, wrong pairing for the surface. Broad lenient tools with
  orchestrator-only recovery: the orchestrator cannot anticipate
  the model's improvisations, so errors fall through silently and
  surface later as bad data. Strict narrow tools with model-side
  recovery on a destructive mutation: the model cheerfully retries
  a refund or a delete because the error came back as text it can
  act on. The recovery decision is the one teams most often skip
  because it has no happy-path demo.

- **What it costs:** the engineering to define and maintain a
  tool surface and its schemas; the discipline to match the
  combination to the stakes of the surface rather than the
  convenience of the SDK default; and the eval cost of testing
  tool-call correctness — right tool, right arguments, correct
  recovery — which is invisible in the happy-path demo.

## 90-second bite

Your feature crossed the line from answering to acting — the
model now calls your API, queries your database, kicks off a
workflow. Eng says it will just add the functions. That is the
trap: tool use looks like one decision and is actually three
coupled ones.

Tool granularity is whether you expose many narrow tools or a few
broad composites. Schema strictness is whether you reject
malformed arguments or coerce them. Error-recovery locus is
whether you feed the tool error back and let the model fix it, or
have a deterministic orchestrator catch and re-prompt. No single
choice is correct on its own. The combination sets the feel, and
there are three good ones:

1. Reliable-workflow is narrow tools plus strict schema plus
   orchestrator recovery. Predictable, auditable, each tool does
   exactly one thing, failures caught deterministically. Right
   when correctness matters more than flexibility: payments, data
   mutations, anything you would put in front of an auditor.

2. Agentic-exploration is broad tools plus lenient schema plus
   model-side recovery. The model improvises and self-heals
   across novel requests. Right for open-ended assistant surfaces
   where range beats predictability.

3. Guided-agent is narrow tools plus strict schema plus
   model-side recovery. A constrained surface, but the model
   fixes its own argument mistakes. Right for semi-structured
   tasks in between.

The failure is not a bad option. It is a mismatched combination.
Broad lenient tools with orchestrator-only recovery let errors
fall through silently because the orchestrator cannot anticipate
the model's improvisations. Strict narrow tools with model-side
recovery on a destructive call let the model cheerfully retry
something dangerous.

The PM call is to name the stakes of the surface, match the
combination to it, and force the recovery decision before launch
rather than after the model retries a payment.

## Depth

Tool use has, over the period 2023 to 2026, become the backbone
of any LLM feature that does more than answer. Adding the
functions hides three independent engineering decisions whose
combination, not whose individual choices, determines whether the
feature is operable.

**Tool granularity.** Narrow means one tool equals one action: a
get-order-status tool, a refund-order tool. The model composes
them, each is trivially testable and auditable, but the model
must orchestrate multi-step sequences and can select the wrong
one. Broad or composite means one tool spans many actions, such
as a manage-order tool with an action parameter. There are fewer
tool-selection errors, but arguments get complex and each tool's
blast radius is larger. Provider guidance converges on using as
few tools as cover the surface, each as narrow as the action's
stakes require. Toolformer (Schick et al., 2023) established that
models can learn when and how to call tools at all; the
production question is no longer whether but how coarsely.

**Schema strictness.** The input schema is a contract. Strict
means rejecting anything that does not validate, with typed,
enum-constrained, required fields, so the model conforms or fails
loudly. Lenient means accepting best-effort and coercing. Strict
is debuggable and safe but produces more hard failures the model
must recover from. Lenient is forgiving but errors propagate
downstream as bad data. Provider strict and structured-output
modes push enforcement into the decoder, making schema-invalid
calls near-impossible at some flexibility cost — OpenAI's strict
structured outputs and equivalent features elsewhere move the
contract from a hope into a guarantee.

**Error-recovery locus.** Tools fail through bad arguments,
downstream errors, or business-rule rejection. Model-side
recovery feeds the error back into context and the model reasons
about it and retries, which is the ReAct observe-act loop (Yao et
al., 2022). It is flexible and handles novel failures but is
non-deterministic and can retry dangerous actions. Orchestrator-
side recovery has a deterministic wrapper catch the error and
decide retry, abort, or escalate, with the model never seeing it.
It is predictable and safe but only handles failures you
anticipated.

**Why the combination is load-bearing.** The three matched
combinations cohere because each decision reinforces the others.
A lenient schema only works with model-side recovery, because the
model is the thing that interprets the coerced result. A strict
schema with orchestrator recovery is coherent because the failure
set is enumerable. Broad tools with a strict schema and
orchestrator recovery is incoherent, because you cannot
deterministically wrap a tool whose surface is open-ended. Pick
lenient schemas and you have implicitly committed to model-side
recovery; the choices are not independent.

**Measurement PMs should ask for by name.** Tool-selection
accuracy is whether the model picked the right tool.
Argument-validity rate is the fraction of calls that pass the
schema. Recovery success rate is, of failed calls, the fraction
that reach a correct end state. The safety-critical one is
unintended-action rate: calls that executed something they should
not have. That last number tells you whether your recovery locus
is safe on a high-stakes surface, and it is the metric a
happy-path demo will never surface.

**Vendor framing.** Anthropic emphasizes tool use and agent
loops, and its Building Effective Agents guidance argues for the
simplest solution first and adding orchestration only when
needed. OpenAI emphasizes strict structured outputs. Google
emphasizes function declarations in Vertex. The trilemma is real;
the framing is partly commercial. Read more than one.

## Decision prompt

Your team is adding tool use to a customer-facing assistant so it
can do things, not just answer — look up an order, check
inventory, and issue a refund. Eng says the plan is to define the
functions and let the model call them.

Before you sign off, scope what good tool use requires here. Name
the three coupled decisions, say which combination you would pick
for the refund capability specifically and why, and call out
where the define-the-functions instinct produces something that
demos well and operates badly. Be specific about the failure the
team would hit in production and what you would want decided
before launch.
