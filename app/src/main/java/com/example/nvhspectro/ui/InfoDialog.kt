package com.example.nvhspectro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.BuildConfig
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhPrimary
import com.example.nvhspectro.theme.NvhSectionContainer

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("NVH Spectro", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Image(
                    painter = painterResource(id = R.drawable.logo_vibratec),
                    contentDescription = "Logo Vibratec",
                    modifier = Modifier.height(28.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, NvhAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = NvhSectionContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfoDetailRow("👤 Auteur", "Louis BARTHELEMY")
                        InfoDetailRow("🏢 Société", "VIBRATEAM [Vibratec (Everenn Group)]")
                        InfoDetailRow("📱 Application", "NVH Spectro")
                        InfoDetailRow("🏷️ Version", "v${BuildConfig.VERSION_NAME}")
                        InfoDetailRow("✉️ Contact", "louis.barthelemy@vibrateam.fr")

                        // Site Web VIBRATEC (Lien cliquable)
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        uriHandler.openUri("https://vibratec.fr/")
                                    },
                        ) {
                            Text(
                                text = "🌐 Site Web VIBRATEC",
                                color = NvhAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "https://vibratec.fr/",
                                    color = NvhPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                )
                                Text(
                                    text = "↗",
                                    color = NvhPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "📜 Description / Métier :",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Application d'analyse acoustique temporelle, spectrogramme d'émergence tonale (TTNR) et télémétrie GPS en temps réel. Construit pour analyse NVH rapide lors de roulage véhicule _ domaine automobile.\n\nElle intègre également le post-traitement synchronisé de vidéos et de fichiers WAV, ainsi qu'un outil de suivi d'ordres dédié à l'extraction des harmoniques pour les Groupes Moto-Propulseurs électriques (GMPe).",
                    fontSize = 12.sp,
                    color = NvhOnSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun InfoDetailRow(
    label: String,
    value: String,
) {
    Column {
        Text(text = label, color = NvhAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = NvhOnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
