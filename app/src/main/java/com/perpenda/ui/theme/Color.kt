package com.perpenda.ui.theme

import androidx.compose.ui.graphics.Color

// Editorial / reference-document palette. Values transcribed 1:1 from the
// design system token source (design handoff colors_and_type.css) and
// docs/roadmap/DESIGN_BRIEF.md. Warm paper + warm ink, hairline rules in warm taupe,
// restrained marginal-ink accents (oxblood / sage / mineral). Completion is
// oxblood, not green — the brief is explicit that nothing uses a "success green".

// --- Light (the canonical mode) ---
val Primary = Color(0xFF7D2A1A)              // oxblood — primary action
val OnPrimary = Color(0xFFFAF7F0)            // paper on oxblood
val PrimaryContainer = Color(0xFFF1E2DC)     // oxblood-tint — reviews-due / focused row
val OnPrimaryContainer = Color(0xFF5A1E13)   // deep oxblood on tint

val Secondary = Color(0xFF5C6B5D)            // sage — "settled"
val OnSecondary = Color(0xFFFAF7F0)
val SecondaryContainer = Color(0xFFE6E8E0)   // sage-tint
val OnSecondaryContainer = Color(0xFF2F362F)

val Tertiary = Color(0xFF46556B)             // mineral — mono / chrome accent
val OnTertiary = Color(0xFFFAF7F0)

val Background = Color(0xFFF5F1E8)           // paper-0 — page
val OnBackground = Color(0xFF1C1815)         // ink-1 — body
val Surface = Color(0xFFFAF7F0)              // paper-1 — card / sheet (warm; not pure white)
val SurfaceVariant = Color(0xFFEDE7D8)       // paper-3 — pull-quote / callout
val OnSurface = Color(0xFF1C1815)            // ink-1
val OnSurfaceVariant = Color(0xFF4A433C)     // ink-2 — meta / secondary

val Outline = Color(0xFFC8BFAE)              // hair-1 — row / section dividers
val OutlineVariant = Color(0xFFE0D9C8)       // hair-2 — sub-dividers
val Error = Color(0xFF8C2017)
val OnError = Color(0xFFFAF7F0)

// --- Dark (mirror of warm-paper, inverted weight) ---
val ColorDarkPrimary = Color(0xFFD6816A)            // oxblood dark
val ColorDarkOnPrimary = Color(0xFF2A1812)
val ColorDarkPrimaryContainer = Color(0xFF3A1F18)   // oxblood-tint dark
val ColorDarkOnPrimaryContainer = Color(0xFFE8C4B8)
val ColorDarkSecondary = Color(0xFFA2AB94)          // sage dark
val ColorDarkOnSecondary = Color(0xFF1F231C)
val ColorDarkSecondaryContainer = Color(0xFF232722) // sage-tint dark
val ColorDarkOnSecondaryContainer = Color(0xFFD7DBCE)
val ColorDarkTertiary = Color(0xFF9DAFC2)           // mineral dark
val ColorDarkOnTertiary = Color(0xFF1A2128)

val ColorDarkBackground = Color(0xFF15130F)         // paper-0 dark
val ColorDarkOnBackground = Color(0xFFE8E2D5)       // ink-1 dark
val ColorDarkSurface = Color(0xFF221E18)            // paper-2 dark
val ColorDarkSurfaceVariant = Color(0xFF2A251D)     // paper-3 dark
val ColorDarkOnSurface = Color(0xFFE8E2D5)
val ColorDarkOnSurfaceVariant = Color(0xFFB5AC9C)   // ink-2 dark
val ColorDarkOutline = Color(0xFF3A352C)            // hair-1 dark
val ColorDarkOutlineVariant = Color(0xFF2A251D)     // hair-2 dark
val ColorDarkError = Color(0xFFE4856E)

// "Completed" is oxblood, not green (DESIGN_BRIEF.md). These retain their
// names so existing components compile, but now resolve to the oxblood family.
val SuccessContainerLight = Color(0xFFF1E2DC)       // oxblood-tint
val OnSuccessContainerLight = Color(0xFF5A1E13)
val SuccessContainerDark = Color(0xFF3A1F18)
val OnSuccessContainerDark = Color(0xFFE8C4B8)

// --- Extended editorial tokens (no Material3 role covers these) ---
// ink-3 tertiary text, raised paper, accent chip tints with their own ink,
// the review-needed banner, error tint. Values 1:1 from colors_and_type.css.
data class PerpendaColors(
    val inkTertiary: Color,       // ink-3 — meta / placeholder / eyebrow
    val paperRaised: Color,       // paper-1 — up-next row, pressed card
    val hairline: Color,          // hair-1 — primary divider
    val hairlineSub: Color,       // hair-2 — sub-divider
    val settledTint: Color,       // sage-tint
    val onSettledTint: Color,
    val contestedTint: Color,     // ochre-tint
    val onContestedTint: Color,
    val unsettledTint: Color,     // mineral-tint
    val onUnsettledTint: Color,
    val bannerTint: Color,        // review-needed banner bg (ochre-tint)
    val onBannerTint: Color,
    val errorTint: Color,
    // Transient success notice (CenterNotice). Deliberately asymmetric:
    // light mode reads as a quiet paper card (same treatment as the About
    // screen's ActionCards), dark mode keeps the oxblood completion tint.
    val noticeTint: Color,
    val onNoticeTint: Color
)

val PerpendaLight = PerpendaColors(
    inkTertiary = Color(0xFF8A8175),
    paperRaised = Color(0xFFFAF7F0),
    hairline = Color(0xFFC8BFAE),
    hairlineSub = Color(0xFFE0D9C8),
    settledTint = Color(0xFFE6E8E0),
    onSettledTint = Color(0xFF3F4B3F),
    contestedTint = Color(0xFFF1E8D0),
    onContestedTint = Color(0xFF6A5012),
    unsettledTint = Color(0xFFE2E6EC),
    onUnsettledTint = Color(0xFF384654),
    bannerTint = Color(0xFFF1E8D0),
    onBannerTint = Color(0xFF6A5012),
    errorTint = Color(0xFFF2DEDB),
    noticeTint = Color(0xFFFAF7F0),     // paper-1 — card look
    onNoticeTint = Color(0xFF1C1815)    // ink-1
)

val PerpendaDark = PerpendaColors(
    inkTertiary = Color(0xFF7C7466),
    paperRaised = Color(0xFF1B1814),
    hairline = Color(0xFF3A352C),
    hairlineSub = Color(0xFF2A251D),
    settledTint = Color(0xFF232722),
    onSettledTint = Color(0xFFC9D0BE),
    contestedTint = Color(0xFF2E2718),
    onContestedTint = Color(0xFFD7B673),
    unsettledTint = Color(0xFF1E242C),
    onUnsettledTint = Color(0xFF9DAFC2),
    bannerTint = Color(0xFF2E2718),
    onBannerTint = Color(0xFFD7B673),
    errorTint = Color(0xFF381914),
    noticeTint = Color(0xFF3A1F18),     // oxblood-tint dark (unchanged look)
    onNoticeTint = Color(0xFFE8C4B8)
)
