// NVH Spectro spacing, alpha and elevation scales [V14 UX-M4, UX-D4, UX-N5].
//
// The audit measured 275 raw dp literals — 58 of them 1–2 dp paddings that made controls
// touch their own borders — plus hand-tuned alphas (0.22, 0.6, 0.7, 0.8) and mixed
// shadow/outline elevation. These objects are the named scales the presentation layer draws
// from; everything snaps to a 4 dp grid.
package com.example.nvhspectro.theme

import androidx.compose.ui.unit.dp

/** 4 dp-grid spacing rhythm. Control content padding never goes below [NvhSpacing.sm]. */
object NvhSpacing {
    /** Hairline gaps between glyph and label inside one control. */
    val xxs = 2.dp

    /** Gaps between sibling chips/badges; compact internal padding on canvas overlays. */
    val xs = 4.dp

    /** Minimum internal padding of any control; default gap inside a group. */
    val sm = 8.dp

    /** Gap between groups inside a card or sheet. */
    val md = 12.dp

    /** Card/sheet internal padding; screen edge margins. */
    val lg = 16.dp

    /** Separation between major surfaces. */
    val xl = 24.dp
}

/** The only alpha values decoration may use — one ladder instead of per-site tuning. */
object NvhAlpha {
    /** Selected-row tint, subtle fills. */
    const val FAINT = 0.12f

    /** Chip fills behind accent text. */
    const val TINT = 0.22f

    /** Section and chip borders. */
    const val OUTLINE = 0.5f

    /** Prominent borders (active accent). */
    const val STRONG = 0.8f
}

/**
 * Tonal-elevation ladder [V14 UX-D4]. Shadows are nearly invisible on a dark theme, so
 * tonal elevation is the single depth mechanism; borders mark semantics, not depth.
 */
object NvhElevation {
    val flat = 0.dp

    /** Cards and the player bar. */
    val raised = 2.dp

    /** Overlays floating above content (sheets, menus). */
    val overlay = 4.dp
}

/**
 * Minimum interactive size [§12, plan 4.4] — the floor for every control an operator has to
 * hit, often wearing gloves, in a moving vehicle. Previously redeclared privately in five
 * files; this is now the one definition.
 */
val NvhMinTouchTarget = 48.dp
