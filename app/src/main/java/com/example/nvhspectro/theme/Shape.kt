// NVH Spectro shape scale [V14 UX-M3].
//
// The audit measured EIGHT ad-hoc corner radii (3–16 dp) across 40 call sites because no
// `Shapes()` was passed to the theme. This is now the single shape language; `ci/checks.sh`
// fails the build if a numeric `RoundedCornerShape(` literal appears outside this package.
package com.example.nvhspectro.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NvhShapes =
    Shapes(
        // Canvas badges and inline status chips.
        extraSmall = RoundedCornerShape(4.dp),
        // Cards-in-cards, list rows, banners.
        small = RoundedCornerShape(8.dp),
        // Cards, sections, the player bar.
        medium = RoundedCornerShape(12.dp),
        // Dialog surfaces.
        large = RoundedCornerShape(16.dp),
        // Bottom sheets, hero surfaces.
        extraLarge = RoundedCornerShape(28.dp),
    )
