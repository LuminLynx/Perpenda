---
id: streaming-ux-bundle-0
slug: streaming-ux
path_id: llm-systems-for-pms
position: 11
prereq_unit_ids:
  - vector-search-rag-bundle-0
status: published
definition: Streaming UX is the decision of how to deliver a model's output progressively — what to stream, how to render it, and how to recover from a mid-stream failure — three coupled choices whose combination determines whether a feature feels responsive, deliberate, or broken.
calibration_tags:
  - claim: "Server-Sent Events is the de facto transport for LLM token streaming across all major providers (Anthropic, OpenAI, Google)."
    tier: settled
  - claim: "Streaming reduces perceived latency by lowering time-to-first-token; it does not reduce total generation time and can slightly increase it."
    tier: settled
  - claim: "Whether deliberately typed-out / artificially-paced rendering improves or degrades UX is contested and surface-dependent — it adds perceived craft on short outputs and slows reading on long ones."
    tier: contested
  - claim: "Whether partial-accept recovery justifies its engineering cost versus full-restart is task- and surface-specific and not generalizable from published guidance."
    tier: contested
  - claim: "Whether falling time-to-first-token as models get faster will make non-streamed responses acceptable again for medium-length outputs is unsettled."
    tier: unsettled
sources:
  - url: "https://docs.anthropic.com/en/api/messages-streaming"
    title: "Anthropic — Streaming Messages (Messages API SSE event sequence)"
    date: 2026-05-16
    primary_source: true
  - url: "https://developers.openai.com/api/docs/guides/streaming-responses"
    title: "OpenAI — Streaming API responses (guide)"
    date: 2026-05-16
    primary_source: true
  - url: "https://docs.cloud.google.com/vertex-ai/generative-ai/docs/reference/rest/v1/projects.locations.endpoints/streamGenerateContent"
    title: "Google Cloud — Vertex AI streamGenerateContent (REST reference; confirms SSE streaming)"
    date: 2026-05-16
    primary_source: true
  - url: "https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events"
    title: "MDN — Using server-sent events (the streaming transport primitive)"
    date: 2026-05-16
    primary_source: true
  - url: "https://www.nngroup.com/articles/response-times-3-important-limits/"
    title: "Nielsen — Response Time Limits (Nielsen Norman Group; the 0.1s / 1s / 10s perception thresholds)"
    date: 2026-05-16
    primary_source: true
  - url: "https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol"
    title: "Vercel AI SDK — Stream Protocols (practitioner reference for streaming granularity)"
    date: 2026-05-16
    primary_source: true
rubric:
  - text: "Names the three coupled streaming decisions — what to stream (raw tokens / semantic chunks / status only), how to render the streamed state (continuous flow / progressive sections / typed-out), and how to recover from a mid-stream failure (silent retry / partial accept / full restart) — AND treats them as coupled, where the combination produces the feel, rather than as three independent toggles."
  - text: "Explains the mechanism behind a mismatched-combination failure — e.g., token-streaming a structured output into a continuous-flow render shows half-parsed garbage the user reads as broken; or no recovery decision being made at all freezes the UI on the first dropped stream."
  - text: "Identifies that the three decisions constrain each other — they cannot be chosen independently; e.g., continuous-flow rendering rules out partial-accept recovery, and token streaming makes partial-accept incoherent — so a coherent feel comes from a combination that reinforces itself, not from optimizing each toggle in isolation."
  - text: "Maps a matched combination to a surface — names at least one coherent triple (chat feel: token + continuous-flow + full-restart; artifact/canvas feel: semantic-chunk + progressive-section + partial-accept; agent-working feel: status-only + status-display + silent-retry) and why it fits that surface — AND recognizes the common PM error: treating 'add streaming' as one decision, defaulting to the chat triple on a surface it doesn't fit, or skipping the recovery decision because it has no happy-path demo."
---

# Streaming UX

## Trade-off framing

- **When this matters:** any LLM feature where generation takes
  long enough that a non-streamed response leaves the user
  watching a spinner — chat surfaces, long-form generation,
  multi-step reasoning UIs. Unit 11 picks up the streaming axis
  from Unit 3 (Latency), where streaming was named as one of three
  latency levers and the *when* question — *is streaming worth it
  on this surface?* — was answered. Unit 11 takes that as settled
  and asks the next question: *given that you're streaming, what
  does streaming well actually require?*

- **The three coupled decisions, and the combinations that
  work:** streaming isn't one decision. It's three: **what to
  stream** (raw tokens / semantic chunks / status only), **how to
  render the streamed state** (continuous flow / progressive
  sections / typed-out), and **how to recover from mid-stream
  failure** (silent retry / partial accept / full restart). The
  decisions are coupled — the combination, not any single choice,
  produces the feel. Three combinations that *match*:
  - *Token streaming + continuous-flow render + full-restart
    recovery* → the **chat feel**: words appear as fast as they
    generate, a dropped stream just restarts the turn. Right for
    conversational surfaces where the unit of value is the whole
    message.
  - *Semantic-chunk streaming + progressive-section render +
    partial-accept recovery* → the **artifact/canvas feel**: a
    document assembles section by section, and a mid-stream
    failure keeps the sections already delivered. Right for
    structured long-form output where partial output still has
    value.
  - *Status-only streaming + status-display render + silent-retry
    recovery* → the **agent-working feel**: the user sees
    "searching… reading… drafting…", never raw tokens, and a
    transient failure retries invisibly. Right for multi-step
    tool-using flows where intermediate tokens would be noise.

- **When this breaks:** a *mismatched* combination — the right
  pieces, wrong pairing for the surface. Two failure modes recur:
  (1) **token streaming + continuous-flow render on a
  structured-output surface** — the user watches half-rendered
  JSON or a markdown table assemble character-by-character and
  reads the feature as broken, when semantic-chunk streaming with
  progressive sections would have felt deliberate; (2) **any
  streaming choice with no recovery decision made at all** — the
  stream dies at 80%, the UI freezes on a half-sentence, and
  because no recovery strategy was picked, the user is stuck with
  garbage. The recovery decision is the one teams most often skip
  because it's invisible in the happy-path demo.

- **What it costs:** the engineering to manage partial-output
  state and a real failure-recovery path (the skipped part); the
  discipline to choose the combination that matches the surface
  rather than defaulting to token-streaming everywhere because
  it's the SDK default; and the test cost of deliberately
  exercising mid-stream failure.

## 90-second bite

Your LLM feature's output takes eight seconds to generate. You're
going to stream it — Unit 3 already settled that. The trap now is
treating *"stream the output"* as one decision. It's three, and
they're coupled.

**What to stream:** raw tokens, semantic chunks, or status only.
**How to render it:** continuous flow, progressive sections, or
typed-out. **How to recover from a mid-stream failure:** silent
retry, partial accept, or full restart. No single choice produces
"good streaming." The *combination* produces a feel — and there
are three good feels, not one:

1. **Chat feel** — token streaming + continuous flow +
   full-restart recovery. Words appear as fast as they generate;
   a dropped stream just restarts the turn. Right when the unit
   of value is the whole message.

2. **Artifact/canvas feel** — semantic-chunk streaming +
   progressive sections + partial-accept recovery. A document
   assembles section by section; a mid-stream failure keeps what
   already arrived. Right when partial structured output still
   has value.

3. **Agent-working feel** — status-only streaming + status
   display + silent retry. The user sees "searching… reading…
   drafting…", never raw tokens; transient failures retry
   invisibly. Right when intermediate tokens would be noise.

The failure isn't picking a "bad" option — it's a *mismatched
combination*. Token-streaming a JSON object into a continuous-flow
UI makes a working feature look broken. And the decision teams
skip most is recovery: it's invisible in the happy-path demo, so
nobody picks a strategy, and the first dropped stream in
production freezes the UI on a half-sentence.

The PM call: name the surface, match the combination to it, and
force the recovery decision *before* launch — not after the first
incident.

## Depth

Streaming UX has, over 2022–2026, become table stakes for any
non-trivial LLM feature — but "add streaming" hides three
independent engineering decisions whose combination, not whose
individual choices, determines the result.

**What to stream.** At the transport layer this is almost always
Server-Sent Events (SSE) — every major provider (Anthropic,
OpenAI, Google) streams completions as an SSE event sequence. The
PM-relevant choice is *granularity*: (a) **raw token deltas** —
lowest latency to first visible output, but the client receives
partial words and incomplete structure; (b) **semantic chunks** —
the server or an intermediate layer buffers until a meaningful
unit (a sentence, a section, a complete JSON field) is ready,
trading a little time-to-first-content for renderable units; (c)
**status events only** — the stream carries progress signals
("retrieving", "reasoning"), not content. Granularity is a
server-and-middleware decision, often invisible to the PM until
the render looks wrong.

**How to render the streamed state.** (a) **Continuous flow** —
append deltas as they arrive; correct for prose, wrong for
anything with structure (a half-parsed markdown table or JSON
object renders as visible garbage mid-stream). (b) **Progressive
sections** — hold a unit until it's complete, then render it
whole; the document grows section by section. (c) **Typed-out** —
deliberately paced character reveal independent of arrival rate.
Typed-out is the most contested render mode: it adds a perception
of craft on short outputs but actively slows reading on long
ones, and it decouples the UI from real generation speed, which
can mask latency regressions.

**How to recover from a mid-stream failure.** Streams drop —
network blips, provider 5xxs, timeouts. The recovery decision has
three shapes: (a) **silent retry** — re-request and resume or
replay; invisible when it works, double-latency when it doesn't.
(b) **partial accept** — keep what arrived, mark the truncation,
let the user act on the partial result; only coherent when the
render mode preserved complete units. (c) **full restart** —
discard and regenerate; the only safe option when partial output
is incoherent (mid-token, mid-JSON). Recovery is the decision
teams skip — it has no happy-path demo, so it's frequently
undecided until the first production incident, at which point the
UI's failure behavior is whatever the framework defaulted to.

**Why the combination is load-bearing.** The three matched
combinations — *chat feel* (token + continuous-flow +
full-restart), *artifact/canvas feel* (semantic-chunk +
progressive-section + partial-accept), *agent-working feel*
(status-only + status-display + silent-retry) — are coherent
because each decision reinforces the others. Token streaming
makes partial-accept incoherent (you'd accept a half-word);
semantic-chunk streaming makes partial-accept *work* (you accept
complete sections). This is why the unit teaches the triple, not
three checkboxes: pick continuous-flow rendering and you've
implicitly constrained your viable recovery strategies.

**Measurement PMs should ask for by name.** **Time to first
token (TTFT)** — the latency the user actually perceives,
distinct from total generation time (Unit 3's distinction,
applied). **Inter-token latency** — jitter here makes streaming
feel stuttery even when TTFT is good. **Stream completion rate**
— the fraction of streams that finish without a recovery event;
the single number that tells you whether your recovery decision
is theoretical or load-bearing. A low completion rate with no
recovery strategy is the silent quality bug behind "the AI
feature feels flaky." The Nielsen Norman response-time thresholds
(0.1s direct-manipulation, 1s flow, 10s attention) are the
backdrop: streaming exists to keep a 10-second generation under
the 1-second perceptual bar for *first content*, not to make the
total faster.

**Vendor framing.** Provider docs describe the transport (SSE
event shapes) but not the UX decision — Anthropic's streaming
docs enumerate `content_block_delta` events, OpenAI's guide
covers the streamed-event sequence, Google's `streamGenerateContent`
returns chunked `GenerateContentResponse` instances. None of
them tell you which render mode or recovery strategy fits your
surface; that's the PM judgment this unit is about. The Vercel
AI SDK's stream-protocol docs are closer to the UX layer but
still stop at "here's how to wire it," not "here's which
combination your surface needs."

## Decision prompt

Your team is about to ship an LLM feature that generates a
structured project brief — headings, bullet sections, and a
short summary table — and takes roughly ten seconds end to end.
Eng says "we'll stream it like the chat feature does." Before you
sign off, scope what streaming *well* requires for this surface.

Name the three coupled streaming decisions, say which combination
you'd pick for this brief-generation surface and why, and call
out where eng's "stream it like chat" instinct produces a feature
that demos fine and operates badly. Be specific about the failure
the team would hit and what you'd want decided before launch.
