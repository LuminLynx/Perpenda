---
id: agents-bundle-0
slug: agents
path_id: llm-systems-for-pms
position: 14
prereq_unit_ids:
  - multimodal-bundle-0
status: published
definition: An agent is a multi-step LLM loop that decides its own next action, and the design is three coupled decisions — how much to decompose, how the loop terminates, and where errors are caught — whose combination determines whether the feature is a reliable pipeline or expensive theater.
calibration_tags:
  - claim: "The reasoning-and-acting loop (the model observes a tool result, reasons, and chooses the next action) is a standard pattern across major providers, formalized as ReAct."
    tier: settled
  - claim: "Per-step error compounds multiplicatively across a multi-step loop — end-to-end reliability is roughly the product of per-step reliability, so 0.9 per step over five steps is ≈ 0.59 end-to-end."
    tier: settled
  - claim: "Cost and latency scale with step count: a model-directed loop pays for every reasoning turn and every tool round-trip, so an N-step task is roughly N× the token spend and serialized latency of a single call."
    tier: settled
  - claim: "Whether most features marketed internally as 'agents' are better served by a fixed workflow than a model-directed loop is contested — vendor guidance leans yes (simplest solution first), but it is task-specific."
    tier: contested
  - claim: "Whether next-generation models will become reliable enough at long-horizon orchestration to make deterministic scaffolding (fixed workflows, external verifiers) unnecessary for most product features is unsettled."
    tier: unsettled
sources:
  - url: "https://arxiv.org/abs/2210.03629"
    title: "ReAct: Synergizing Reasoning and Acting in Language Models (Yao, Zhao, Yu, Du, Shafran, Narasimhan, Cao)"
    date: 2022-10-06
    primary_source: true
  - url: "https://arxiv.org/abs/2201.11903"
    title: "Chain-of-Thought Prompting Elicits Reasoning in Large Language Models (Wei, Wang, Schuurmans, Bosma, Ichter, Xia, Chi, Le, Zhou)"
    date: 2022-01-28
    primary_source: true
  - url: "https://arxiv.org/abs/2303.11366"
    title: "Reflexion: Language Agents with Verbal Reinforcement Learning (Shinn, Cassano, Berman, Gopinath, Narasimhan, Yao)"
    date: 2023-03-20
    primary_source: true
  - url: "https://www.anthropic.com/news/building-effective-agents"
    title: "Anthropic — Building effective agents (workflows vs. agents; use the simplest solution and add complexity only when it demonstrably improves outcomes)"
    date: 2026-05-22
    primary_source: true
  - url: "https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview"
    title: "Anthropic — Tool use with Claude (the agent loop's mechanical substrate)"
    date: 2026-05-22
    primary_source: true
rubric:
  - text: "Names the three coupled agent decisions — how much to decompose (single call / fixed workflow / model-directed loop), how the loop terminates (fixed step budget / model self-assessment / external verifier), and where errors are caught (let them compound / per-step validation / end-state check) — AND treats them as coupled, where the combination sets the feel, rather than as three independent toggles."
  - text: "Explains the mechanism behind a mismatched-combination failure — why it happens, not just that it happens — e.g., a model-directed loop with no step budget and no per-step validation lets compounding error and runaway cost build until the end state is wrong and the bill is large, because reliability is the product of per-step reliability and cost scales with step count; or a fixed workflow that lets errors compound ships a confidently-wrong end state because nothing checks the intermediate steps."
  - text: "Identifies that the three decisions constrain each other — they cannot be chosen independently; e.g., a model-directed loop forces a termination guard (a step budget or an external verifier) because the model cannot be trusted to stop itself, and compounding error is only survivable on a short fixed workflow — so a coherent design is a combination whose legs reinforce each other, not three toggles set in isolation."
  - text: "Maps a matched combination to a task shape — names at least one coherent triple (reliable-pipeline: fixed workflow + step budget + per-step validation, for tasks whose steps are knowable in advance; true-agent: model-directed loop + external verifier + end-state check, for tasks where decomposition genuinely cannot be predetermined; just-prompt-it: single call, for tasks that need no decomposition) and why it fits that task — AND names the common PM error of building a model-directed loop for a task that is actually a fixed workflow, paying agent cost, latency, and unpredictability for zero capability gain (the 'expensive theater')."
---

# Agents / multi-step reasoning

## Trade-off framing

- **When this matters:** any feature where the model does more than
  answer in one shot — it plans, calls tools, reads the results, and
  decides what to do next, possibly over many turns. This is the
  pattern behind "research assistants," "do-it-for-me" automations,
  and anything described internally as an "agent." Unit 14 is
  *downstream* of Unit 12 (Tool use): an agent is a tool-use loop with
  the model in the driver's seat, so the tool-call mechanics from
  Unit 12 are assumed here. The PM-visible question is not "can we
  build an agent" but "does this task actually need a model-directed
  loop, or is it a fixed workflow wearing an agent costume?"

- **The three coupled decisions, and the combinations that work:**
  building an agent is not one decision. It is three. **How much to
  decompose** is whether the task is a single call, a fixed
  predetermined workflow, or a loop where the model decides its own
  next step. **How the loop terminates** is whether it stops on a
  fixed step budget, on the model's own self-assessment that it is
  done, or on an external verifier that checks the goal is met. **Where
  errors are caught** is whether intermediate errors are allowed to
  compound, caught by per-step validation, or caught only by a
  final end-state check. The decisions are coupled — the combination,
  not any single choice, produces the feel. Two combinations that
  match:
  - *Fixed workflow + step budget + per-step validation* → the
    **reliable-pipeline feel**: the steps are knowable in advance,
    each one is checked, and the loop cannot run away. Right for the
    large majority of tasks marketed as "agents," which are actually
    predetermined sequences.
  - *Model-directed loop + external verifier + end-state check* → the
    **true-agent feel**: the model chooses its own path, an
    independent check decides when the goal is met, and the end state
    is verified. Right for the rare task where the steps genuinely
    cannot be predetermined — and worth its cost only there.
  - *Single call* → the **just-prompt-it baseline**: no loop at all,
    for tasks that need no decomposition. Often the honest answer.

- **When this breaks:** a *mismatched* combination — the right pieces,
  wrong pairing for the task. Two failure modes recur: (1) a
  **model-directed loop with no step budget and no per-step
  validation** — compounding error and runaway cost build until the
  end state is wrong and the bill is large, because end-to-end
  reliability is the product of per-step reliability and cost scales
  with step count; (2) a **fixed workflow that lets errors compound** —
  nothing checks the intermediate steps, so a wrong step early ships a
  confidently-wrong final answer. The decision teams most often skip is
  termination and error-catching, because the happy-path demo never
  runs long enough to drift.

- **What it costs:** the discipline to diagnose whether the task's
  steps are knowable in advance before reaching for a loop; the
  engineering to build a real termination guard and validation path
  (the skipped part); and the eval cost of testing the loop on inputs
  that make it drift, not just the demo input.

## 90-second bite

Your feature stopped answering and started *doing* — planning, calling
tools, reading results, deciding the next step. Eng calls it an agent.
The trap is treating "build an agent" as one decision. It is three, and
they are coupled.

**How much to decompose:** a single call, a fixed workflow you wrote in
advance, or a loop where the model picks its own next step. **How it
terminates:** a fixed step budget, the model deciding it is done, or an
external verifier checking the goal is met. **Where errors are caught:**
let them compound, validate each step, or check only the end state. No
single choice is right alone. The combination sets the feel, and there
are three good ones:

1. **Reliable-pipeline** — fixed workflow + step budget + per-step
   validation. The steps are knowable ahead of time, each is checked,
   the loop cannot run away. This fits most "agent" use cases, because
   most of them are actually predetermined sequences.

2. **True-agent** — model-directed loop + external verifier + end-state
   check. The model chooses its own path; an independent check decides
   when it is done. Right only when the steps genuinely cannot be
   predetermined — rare, expensive, and worth it exactly there.

3. **Just-prompt-it** — a single call, no loop. For tasks that need no
   decomposition. Often the honest answer nobody wants to hear.

The failure is not a bad option. It is a mismatched combination — and
the most expensive one is a model-directed loop on a task that was a
fixed workflow all along. You pay for every reasoning turn and tool
round-trip, latency stacks up serially, and the model can wander,
all for zero capability gain over a workflow you could have written.
That is the **expensive theater**: it demos like magic and operates
like a liability.

Two numbers make the stakes concrete. Reliability *multiplies* across
steps — 90%-reliable steps over five steps is about 59% end-to-end, not
90%. And cost *adds up* with steps — an N-step loop is roughly N× the
spend and serialized latency of one call. A loop with no step budget and
no per-step check is a feature that gets less reliable and more expensive
the longer it runs.

The PM call: diagnose whether the task's steps are knowable in advance,
match the combination to that shape, and force the termination and
error-catching decisions *before* launch — not after the loop bills
$400 wandering toward a wrong answer.

## Depth

Agents are the point where LLM features stop being a function call and
become a control loop. The mechanical substrate is Unit 12's tool use:
the model emits a tool call, your code runs it, the result goes back
into context, the model decides what to do next. ReAct (Yao et al.,
2022) formalized this *reasoning-and-acting* loop; chain-of-thought
(Wei et al., 2022) is the reasoning step inside it; Reflexion (Shinn et
al., 2023) added a self-verification turn. What changes for a PM is that
"give the model a loop" hides three independent decisions whose
combination, not whose individual choices, determines whether the
feature is operable.

**How much to decompose.** A *single call* does the whole task in one
shot — no loop, lowest cost and latency, right when the task needs no
intermediate steps. A *fixed workflow* is a sequence you design in
advance — call A, then B, then C — where the model fills each step but
does not choose the order; predictable, testable, and the right shape
for the large majority of multi-step tasks, because most multi-step
tasks have knowable steps. A *model-directed loop* lets the model decide
its own next action each turn; maximally flexible, and the only option
when the steps genuinely cannot be predetermined — but also the most
expensive and least predictable. Anthropic's *Building Effective Agents*
guidance is blunt about the ordering: use the simplest thing that works,
and add agency only when it demonstrably improves outcomes.

**How the loop terminates.** A loop that cannot stop itself is an outage
waiting to happen. A *fixed step budget* caps the number of turns —
simple, safe, but blunt (it can cut off a task that needed one more
step). *Model self-assessment* lets the model declare it is done —
flexible, but the model is an unreliable judge of its own completion and
can stop early or never. An *external verifier* is a separate check —
deterministic code, a second model, or a human — that decides whether
the goal is actually met; the most reliable, the most engineering. The
termination choice is not free: a model-directed loop with only
self-assessment is the classic runaway.

**Where errors are caught.** *Letting errors compound* means no
intermediate checks — viable only on a short fixed workflow where the
blast radius is small. *Per-step validation* checks each step's output
before the next consumes it — catches drift early, the reliable-pipeline
default. An *end-state check* validates only the final result — cheaper
than per-step, but on a long loop it tells you the answer is wrong after
you have already paid for every step that produced it.

**Why the combination is load-bearing.** The three matched combinations
— *reliable-pipeline* (fixed workflow + step budget + per-step
validation), *true-agent* (model-directed loop + external verifier +
end-state check), and *just-prompt-it* (single call) — cohere because
each decision reinforces the others. A model-directed loop *forces* a
termination guard, because the model cannot be trusted to stop itself —
and for an open-ended goal a blunt step budget cannot tell whether the
goal is actually met, so the true-agent shape's termination leg is an
*external verifier* (stop when the goal checks out), with a step budget
kept only as a cost-ceiling backstop, not as the defining guard.
Compounding error is only survivable
on a short fixed workflow; the longer and more model-directed the loop,
the more per-step validation stops being optional. Pick a model-directed
loop and you have implicitly committed to a real termination guard and
real validation — the choices are not independent.

**The compounding-error mechanism, made concrete.** Reliability across a
sequence is roughly the product of per-step reliability. A step that is
90% reliable, run five times in sequence with no validation, yields
0.9⁵ ≈ 0.59 — a feature that works in the demo (one step, 90%) and fails
two times in five in production (five steps, 59%). Per-step validation
breaks the multiplication by catching and retrying a bad step before it
propagates; an external verifier catches a wrong end state before it
ships. This is why the error-catching decision is load-bearing and why
skipping it is invisible until the loop runs long enough to drift.

**The cost mechanism (Unit 8 applied).** Every loop turn is a model call
plus, usually, a tool round-trip. An N-step loop is roughly N× the token
spend of a single call, and the latency is *serialized* — the user waits
for the sum, not the max. A model-directed loop with no step budget has
no cost ceiling; "expensive theater" is fundamentally a cost argument
before it is a reliability one. Annualize at projected volume (Unit 8):
a loop that averages eight steps at $0.02/step is $0.16/run, and at
10k runs/day that is ~$580k/year for a feature a fixed three-step
workflow might have delivered at a third the cost and twice the
reliability.

**Measurements PMs should ask for by name.** *Steps-per-run at p50 and
p95* — the tail is where runaway cost and latency live; a p50 of 4 with
a p95 of 30 is a loop that usually behaves and occasionally bills like a
disaster. *End-to-end task success rate on representative inputs* — not
the per-step rate, the whole-loop rate, measured (Unit 4) on inputs that
make the loop drift, not the demo input. *Cost-per-successful-run* — the
honest unit economics, since failed runs still cost full price. A loop
without these three numbers is a loop you cannot scope or defend.

**Vendor framing.** Provider guidance has converged on "workflows first,
agents only when needed" — Anthropic's *Building Effective Agents* makes
the workflow-vs-agent distinction central and argues for the simplest
solution. Framework marketing pushes the other way, because an
autonomous agent demos better than a three-step pipeline. The trilemma
is real; the bias toward "make it an agent" is partly commercial. Read
the engineering guidance, not the launch posts.

## Decision prompt

Your team is building an internal "deal-desk assistant" that, given a
sales opportunity, pulls the account's contract history, checks current
pricing rules, flags any non-standard terms, and drafts an approval
summary for finance. Eng's plan: "give the model the tools and let it
figure out the steps — it's an agent." A prototype works impressively in
the demo on a clean opportunity.

Before you sign off, scope what good multi-step design requires here.
Name the three coupled decisions, say which combination you would pick
for this deal-desk task and why the task's shape drives that choice, and
call out where the "let the model figure out the steps" instinct
produces something that demos well and operates badly. Be specific about
the failure the team would hit in production — and what you would want
decided about termination and error-catching before launch.
