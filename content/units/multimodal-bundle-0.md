---
id: multimodal-bundle-0
slug: multimodal
path_id: llm-systems-for-pms
position: 13
prereq_unit_ids:
  - tool-use-bundle-0
status: published
definition: Multimodal vision is the decision of how to turn an image into a signal your system can act on — send the raw image to a vision model, extract structured signal first with classical CV, or a hybrid of both — chosen by the shape of the visual task rather than by reaching for the most capable model.
calibration_tags:
  - claim: "All major providers accept image input natively — the model converts the image into visual tokens and processes them alongside text in a single context."
    tier: settled
  - claim: "Image input is billed in tokens proportional to resolution/detail; a single high-detail image can cost as much as thousands of text tokens, making it a distinct and often dominant cost line."
    tier: settled
  - claim: "Whether a VLM or a purpose-built classical-CV/OCR pipeline yields better accuracy on a given stable-extraction task is task-specific and not generalizable from published benchmarks."
    tier: contested
  - claim: "Whether hybrid (cheap extractor plus VLM fallback) beats VLM-only at production scale depends on the task's stable fraction and the cost delta, and is workload-dependent."
    tier: contested
  - claim: "Whether falling VLM cost-per-image will eventually make purpose-built extraction pipelines unnecessary for most stable tasks is unsettled."
    tier: unsettled
sources:
  - url: "https://platform.claude.com/docs/en/build-with-claude/vision"
    title: "Anthropic — Vision (image input, sizing, token cost)"
    date: 2026-05-18
    primary_source: true
  - url: "https://developers.openai.com/api/docs/guides/images-vision"
    title: "OpenAI — Images and vision (detail levels and image-token cost mechanic)"
    date: 2026-05-18
    primary_source: true
  - url: "https://docs.cloud.google.com/vertex-ai/generative-ai/docs/samples/googlegenaisdk-textgen-with-txt-img"
    title: "Google Cloud — Vertex AI text generation with text and image input"
    date: 2026-05-18
    primary_source: true
  - url: "https://arxiv.org/abs/2010.11929"
    title: "An Image is Worth 16x16 Words: Transformers for Image Recognition at Scale (Dosovitskiy, Beyer, Kolesnikov, Weissenborn, Zhai, Unterthiner, Dehghani, Minderer, Heigold, Gelly, Uszkoreit, Houlsby)"
    date: 2020-10-22
    primary_source: true
  - url: "https://arxiv.org/abs/2103.00020"
    title: "Learning Transferable Visual Models From Natural Language Supervision (CLIP) (Radford, Kim, Hallacy, Ramesh, Goh, Agarwal, Sastry, Askell, Mishkin, Clark, Krueger, Sutskever)"
    date: 2021-02-26
    primary_source: true
  - url: "https://docs.cloud.google.com/document-ai/docs/enterprise-document-ocr"
    title: "Google Cloud — Document AI Enterprise Document OCR (classical-extraction anchor)"
    date: 2026-05-18
    primary_source: true
rubric:
  - text: "Names the three approaches — raw image to a vision model, extract structured signal first with OCR or classical CV, and a hybrid of a cheap extractor with a VLM fallback — AND frames the decision as diagnosing the shape of the visual task first, not picking by which model is most capable."
  - text: "Explains the mechanism behind an approach/shape mismatch — a VLM on a barcode or a fixed-format form overpays and loses evaluability for zero capability gain, while a brittle classical-CV pipeline on open-ended understanding does not degrade gracefully, it simply fails outside its training distribution — AND identifies the cost/eval consequence, that image tokens are a distinct and often dominant cost line and that free-text output over arbitrary images is hard to evaluate."
  - text: "Maps an approach to a task shape correctly — open-ended understanding to a VLM, stable bounded extraction to extract-first, high-volume mostly-stable-with-a-tail to hybrid — and explains why the shape drives the choice AND names the common PM error of reaching for the most capable model by default instead of diagnosing the task shape."
---

# Multimodal (vision basics)

## Trade-off framing

- **When this matters:** any feature where an image is an input —
  a receipt, a screenshot, a product photo, a chart, a scanned
  form. The PM-visible question is not "can the model see images"
  (every frontier model can) but "what is the *shape* of this
  visual task, and which approach matches it."

- **The three approaches, mapped to task shape:**
  - **Raw image to a vision model.** For open-ended visual
    understanding — describe this scene, answer questions about
    this chart, reason about this UI screenshot. Flexible, zero
    pipeline, works the moment you turn it on. Costs: expensive
    per image, where image tokens are a large and often dominant
    line, and hard to eval, because the output is free text over
    arbitrary images.
  - **Extract structured signal first, with OCR, classical CV,
    or a purpose-built detector.** For stable, well-defined
    extraction — read the total off this receipt, is there a
    face, decode this barcode. Cheap, precise, deterministic,
    trivially evaluable. Cost: brittle — it fails on inputs it
    was not built for, with no graceful degradation.
  - **Hybrid: a cheap extractor with a VLM fallback on low
    confidence.** For high-volume tasks that are mostly stable
    with a long tail — 95 percent of receipts are standard, 5
    percent are weird. Best cost and quality at scale. Cost: the
    most engineering, and a confidence threshold you have to tune
    and monitor.

- **When this breaks:** an approach/shape mismatch. Throwing the
  image at a VLM for a barcode or a fixed-format form means you
  overpay per call and *lose* evaluability for zero capability
  gain. Or building a brittle classical-CV pipeline for
  "understand this arbitrary screenshot" — it does not degrade,
  it just fails on everything outside its training distribution.
  The PM error is reaching for the most capable model by default
  instead of diagnosing the task shape first.

- **What it costs:** the discipline to classify the visual task
  before picking an approach; the willingness to measure
  cost-per-image and eval-tractability as first-class, because
  image tokens are a distinct and often dominant cost line (Unit
  8 applied); and, for the hybrid, the engineering plus a
  live-tuned confidence threshold.

## 90-second bite

There is an image in your feature — a receipt, a screenshot, a
chart, a product photo — and the model can see it. Eng's instinct
is to send the image straight to the vision model. That is the
trap: "can the model see it" is not the question. The question is
what shape the visual task is, because three different approaches
fit three different shapes, and reaching for the most capable
model by default is how you overpay and lose the ability to
measure quality.

Three approaches, three task shapes:

1. Open-ended understanding maps to the raw image going to a
   vision model. Describe this scene, reason about this UI, answer
   questions about this chart. The VLM is the only thing that
   handles the unbounded case. It works the moment you turn it on.
   You pay for it in image tokens, a large and often dominant cost
   line, and in eval difficulty, because the output is free text
   over arbitrary inputs.

2. Stable, well-defined extraction maps to pulling the signal
   first with classical CV or OCR. Read the total off a receipt,
   decode a barcode, detect a face. Cheap, deterministic,
   trivially testable. The cost is brittleness: it fails hard on
   anything it was not built for, with no graceful middle.

3. Mostly-stable-with-a-tail at volume maps to the hybrid. A
   cheap extractor handles the 95 percent, the VLM catches the
   weird 5 percent. Best cost and quality at scale, but it is the
   most engineering and a confidence threshold you have to tune
   and watch.

The PM call is to classify the task shape before picking the
approach. A VLM on a barcode overpays and loses evaluability for
nothing. A rigid CV pipeline on "understand this arbitrary
screenshot" simply fails. Diagnose first, reach for capability
second.

## Depth

Multimodal vision has, over the period 2021 to 2026, become a
default capability — every frontier model accepts image input.
That ubiquity is exactly the trap: "the model can see it" makes
the expensive approach feel free, when the right approach depends
entirely on the task's shape.

**How a vision model sees an image.** The model splits the image
into patches, projects each into the same embedding space as text
tokens, and processes them in one context alongside the prompt.
The lineage runs through ViT (Dosovitskiy et al., 2020), which
established the patches-to-tokens mechanic, and CLIP (Radford et
al., 2021), which aligned image and text representations so a
model can answer open-ended questions about an image at all. The
PM-relevant consequence is economic: image input is billed in
tokens roughly proportional to resolution and detail, and a
single high-detail image can cost as many tokens as a long
document. Image tokens are a distinct and often dominant cost
line — this is Unit 8 applied, and it is the fact the whole
trade-off rests on.

**Approach 1: raw image to a VLM.** Right for open-ended visual
understanding — describe, reason, answer arbitrary questions over
arbitrary images. It is the only approach that handles the
unbounded case, and it works with zero pipeline. The two costs
are the per-image token bill and eval tractability: free-text
output over arbitrary images is hard to grade without a second
model or a human, so quality is hard to *know*, not just hard to
get.

**Approach 2: extract structured signal first.** Classical CV,
OCR, or a purpose-built detector turns the image into a typed
value before any LLM is involved — the receipt total, the
barcode, a face bounding box. Cheap, deterministic, and trivially
evaluable with exact-match metrics. The cost is brittleness with
no graceful degradation: it succeeds on its trained distribution
and fails hard outside it. Production OCR and document services
such as Google Document AI, AWS Textract, and Tesseract are the
mature tooling here.

**Approach 3: hybrid.** A cheap extractor handles the stable
majority; low-confidence cases fall back to a VLM. Right for
high-volume tasks that are mostly stable with a long tail — most
receipts are standard, a few are photographed at an angle in poor
light. Best cost and quality at scale, but it is the most
engineering, and the confidence threshold that routes to the
fallback is a live parameter you must tune and monitor; set it
wrong and you either pay for VLM calls you did not need or pass
bad extractions through.

**Diagnosing task shape.** The PM discipline is a classification
done before any approach is picked. Is the task unbounded, an
understanding task, or bounded, an extraction task? If bounded,
is it uniformly stable, or mostly-stable-with-a-tail at volume?
Unbounded maps to the VLM. Bounded and uniform maps to
extract-first. Bounded, high-volume, and long-tailed maps to
hybrid. The recurring error is skipping this classification and
reaching for the VLM because it is the most capable — paying its
cost and surrendering evaluability for a barcode.

**Measurements PMs should ask for by name.** Cost-per-image at
the resolution you actually send, not the default. Extraction
accuracy via exact-match where the task is bounded. For VLM
outputs, eval via forced structured output so the free text
becomes gradable. For hybrid, the fallback rate and the
threshold's precision and recall — the number that tells you
whether the threshold is tuned or theoretical.

**Vendor framing.** Provider docs lead with the VLM path because
that is the product they sell; none of them will tell you a
barcode scanner is the better answer. The trilemma is real; the
vendor bias toward "send us the image" is structural. Read the
classical-extraction tooling docs too, or the unit's own warning
becomes the thing you violate.

## Decision prompt

Your team is shipping a feature that auto-fills an expense from a
photo of a receipt the user uploaded — vendor, date, total,
maybe line items. Eng's plan is to send each receipt image
straight to the vision model and parse what comes back, because
the model is good enough to read a receipt.

Before you sign off, scope what good multimodal design requires
here. Name the three approaches, classify the shape of this
visual task and say which approach you would pick for the receipt
auto-fill specifically and why the shape drives that, and call
out where the send-it-to-the-VLM instinct produces something that
demos well and operates badly. Be specific about the failure the
team would hit in production and what you would want decided
before launch.
