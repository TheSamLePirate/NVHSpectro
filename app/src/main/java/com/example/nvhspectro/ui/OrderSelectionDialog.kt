package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhOutline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSelectionDialog(
    currentOrder: Double,
    onOrderSelected: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(1.0, 2.0, 6.0, 8.0, 12.0, 16.0, 18.0, 24.0, 36.0, 48.0)
    var customOrderText by remember { mutableStateOf(if (presets.contains(currentOrder)) "" else currentOrder.toString()) }
    var selectedVal by remember { mutableStateOf(currentOrder) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.order_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                Text(
                    text = stringResource(R.string.order_dialog_help),
                    fontSize = 12.sp,
                    color = Color.LightGray,
                )

                // Presets d'ordres courants en grille 5x2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(84.dp),
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
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) NvhAccent else MaterialTheme.colorScheme.surfaceVariant,
                            modifier =
                                Modifier
                                    .clickable {
                                        selectedVal = ord
                                        customOrderText = ""
                                    }.border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) NvhOnSurface else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = ordName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
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
                    label = { Text(stringResource(R.string.order_dialog_custom_label), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvhAccent,
                            unfocusedBorderColor = NvhOutline,
                        ),
                )

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = NvhOnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onOrderSelected(selectedVal)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NvhAccent),
                    ) {
                        Text(
                            stringResource(R.string.order_dialog_apply, selectedVal),
                            color = NvhCanvas,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
