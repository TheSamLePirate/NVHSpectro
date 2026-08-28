package com.example.nvhspectro.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhSpacing

/**
 * Tracked-order picker. An `AlertDialog` [V14 UX-M7]: this is a short, focused choice —
 * the raw `Dialog{Surface}` construction with its own title/padding/button conventions was
 * one of two competing dialog languages in the app.
 */
@Composable
fun OrderSelectionDialog(
    currentOrder: Double,
    onOrderSelected: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(1.0, 2.0, 6.0, 8.0, 12.0, 16.0, 18.0, 24.0, 36.0, 48.0)
    var customOrderText by remember { mutableStateOf(if (presets.contains(currentOrder)) "" else currentOrder.toString()) }
    var selectedVal by remember { mutableStateOf(currentOrder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.order_dialog_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = NvhOnSurfaceVariant,
                )

                // Presets d'ordres courants en grille 5x2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PRESET_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                    modifier = Modifier.height(PRESET_GRID_HEIGHT),
                ) {
                    items(presets) { ord ->
                        val isSelected = (ord == selectedVal) && customOrderText.isEmpty()
                        val ordName =
                            if (ord % 1.0 == 0.0) {
                                stringResource(R.string.order_integer, ord.toInt())
                            } else {
                                stringResource(R.string.order_fractional, ord)
                            }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) NvhAccent else MaterialTheme.colorScheme.surfaceVariant,
                            modifier =
                                Modifier
                                    .clickable {
                                        selectedVal = ord
                                        customOrderText = ""
                                    }.border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) NvhOnSurface else Color.Transparent,
                                        shape = MaterialTheme.shapes.small,
                                    ),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = NvhSpacing.sm),
                            ) {
                                Text(
                                    text = ordName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) NvhCanvas else NvhOnSurface,
                                )
                            }
                        }
                    }
                }

                // Saisie libre
                OutlinedTextField(
                    value = customOrderText,
                    onValueChange = { input ->
                        customOrderText = input
                        val parsed = input.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            selectedVal = parsed
                        }
                    },
                    label = { Text(stringResource(R.string.order_dialog_custom_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvhAccent,
                            unfocusedBorderColor = NvhOutline,
                        ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOrderSelected(selectedVal)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NvhAccent, contentColor = NvhCanvas),
            ) {
                Text(stringResource(R.string.order_dialog_apply, selectedVal))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = NvhOnSurfaceVariant)
            }
        },
    )
}

private const val PRESET_COLUMNS = 5
private val PRESET_GRID_HEIGHT = 96.dp
