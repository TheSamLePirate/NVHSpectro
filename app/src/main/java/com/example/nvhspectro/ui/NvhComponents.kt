// Shared presentation components [V14 UX-D1, UX-M7, UX-D10, UX-D12].
//
// The audit's structural finding: every visual decision (radius, padding, title style,
// border) was made per call site, so eight corner radii and 27 font sizes accumulated by
// drift. These components centralise the decisions the dialogs and panes share; a visual
// change is made once, here.
package com.example.nvhspectro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhSectionContainer
import com.example.nvhspectro.theme.NvhSpacing

/**
 * A titled, accent-bordered section inside a dialog or sheet [V14 UX-D12].
 *
 * One construction for what used to be three hand-built Card+border+title blocks with
 * different paddings and font sizes in `SettingsDialog` alone.
 */
@Composable
fun NvhSection(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = NvhAlpha.OUTLINE), MaterialTheme.shapes.small),
        colors = CardDefaults.cardColors(containerColor = NvhSectionContainer),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(NvhSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/**
 * The one modal-sheet idiom for the app's large configuration surfaces [V14 UX-M7].
 *
 * `SettingsDialog`, `KinematicsDialog` and `EmergenceReportDialog` used two competing dialog
 * languages (scrolling `AlertDialog` vs raw `Dialog{Card}`); a bottom sheet with a drag
 * handle and full-height expansion is the current Android idiom for exactly this. Content
 * manages its own scrolling (a sheet body may hold a `LazyColumn`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvhSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = NvhSpacing.lg, end = NvhSpacing.lg, bottom = NvhSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                titleTrailing?.invoke()
            }
            content()
        }
    }
}

/**
 * Status shapes drawn as vectors [V14 UX-D10]. Colour is never the only channel (§12,
 * plan 4.4): each state keeps its own SHAPE — previously text glyphs (`●`/`▲`/`✕`/`⛔`)
 * whose size followed the font scale and whose look depended on the OEM emoji font.
 */
enum class NvhGlyphShape { DOT, TRIANGLE, CROSS, BLOCKED }

@Composable
fun NvhStatusGlyph(
    shape: NvhGlyphShape,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.18f
        when (shape) {
            NvhGlyphShape.DOT -> drawCircle(color = color, radius = w / 2f)
            NvhGlyphShape.TRIANGLE -> {
                val path =
                    Path().apply {
                        moveTo(w / 2f, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                drawPath(path, color)
            }
            NvhGlyphShape.CROSS -> {
                val inset = w * 0.12f
                drawLine(color, Offset(inset, inset), Offset(w - inset, h - inset), stroke, StrokeCap.Round)
                drawLine(color, Offset(w - inset, inset), Offset(inset, h - inset), stroke, StrokeCap.Round)
            }
            NvhGlyphShape.BLOCKED -> {
                val r = w / 2f - stroke / 2f
                drawCircle(color = color, radius = r, style = Stroke(width = stroke))
                // Diagonal bar of the "no entry" sign, clipped to the circle by geometry.
                val d = r * 0.7071f
                drawLine(
                    color,
                    Offset(w / 2f - d, h / 2f - d),
                    Offset(w / 2f + d, h / 2f + d),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}
