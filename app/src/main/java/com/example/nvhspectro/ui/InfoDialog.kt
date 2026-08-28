package com.example.nvhspectro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    val websiteUrl = stringResource(R.string.about_website_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Image(
                    painter = painterResource(id = R.drawable.logo_vibratec),
                    contentDescription = stringResource(R.string.cd_logo_vibratec),
                    modifier = Modifier.height(28.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        text = {
            Column(
                // Scrollable: the dialog now carries the diagnostics section too, and it
                // must stay reachable at large font scales [§12, plan 4.4].
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                        InfoDetailRow(stringResource(R.string.about_author_label), stringResource(R.string.about_author))
                        InfoDetailRow(stringResource(R.string.about_company_label), stringResource(R.string.about_company))
                        InfoDetailRow(stringResource(R.string.about_app_label), stringResource(R.string.app_title))
                        InfoDetailRow(
                            stringResource(R.string.about_version_label),
                            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        )
                        InfoDetailRow(stringResource(R.string.about_contact_label), stringResource(R.string.about_contact))

                        // Site Web VIBRATEC (Lien cliquable)
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        uriHandler.openUri(websiteUrl)
                                    },
                        ) {
                            Text(
                                text = stringResource(R.string.about_website_label),
                                color = NvhAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = websiteUrl,
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

                DiagnosticsSection()

                Text(
                    text = stringResource(R.string.about_description_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_description),
                    fontSize = 12.sp,
                    color = NvhOnSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold)
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
