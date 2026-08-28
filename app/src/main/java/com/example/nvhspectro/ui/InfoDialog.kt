package com.example.nvhspectro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.BuildConfig
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhPrimary
import com.example.nvhspectro.theme.NvhSpacing

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
                Text(stringResource(R.string.app_title), style = MaterialTheme.typography.titleLarge)
                Image(
                    painter = painterResource(id = R.drawable.logo_vibratec),
                    contentDescription = stringResource(R.string.cd_logo_vibratec),
                    modifier = Modifier.height(LOGO_HEIGHT),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        text = {
            Column(
                // Scrollable: the dialog now carries the diagnostics section too, and it
                // must stay reachable at large font scales [§12, plan 4.4].
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
            ) {
                NvhSection(
                    title = stringResource(R.string.about_app_label),
                    accent = NvhAccent,
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
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                        ) {
                            Text(
                                text = websiteUrl,
                                color = NvhPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = TextDecoration.Underline,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = NvhPrimary,
                                modifier = Modifier.size(LINK_ICON_SIZE),
                            )
                        }
                    }
                }

                DiagnosticsSection()

                Text(
                    text = stringResource(R.string.about_description_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = NvhOnSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
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
        Text(text = label, color = NvhAccent, style = MaterialTheme.typography.labelMedium)
        Text(text = value, color = NvhOnSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

private val LOGO_HEIGHT = 28.dp
private val LINK_ICON_SIZE = 14.dp
