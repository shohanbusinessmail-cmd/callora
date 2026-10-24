package com.shohankhan.callora.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.InfoRow
import com.shohankhan.callora.core.ui.R

/**
 * Screen 15: Settings, Backup/Import, Diagnostics & Maintenance Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    statusBannerMessage: String?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleLowBatteryAlert: (Boolean) -> Unit,
    onChangeLowBatteryThreshold: (Int) -> Unit,
    onToggleChargingPrompt: (Boolean) -> Unit,
    onToggleRebootCheck: (Boolean) -> Unit,
    onTogglePreferDirectUssd: (Boolean) -> Unit,
    onExportBackupToUri: (Uri) -> Unit,
    onImportBackupFromUri: (Uri) -> Unit,
    onExportDiagnosticsToUri: (Uri) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportBackupToUri(uri) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportBackupFromUri(uri) }

    val exportDiagLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportDiagnosticsToUri(uri) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        if (!statusBannerMessage.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = statusBannerMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(CalloraSpacing.md)
                    )
                }
            }
        }

        // Language & Appearance
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_appearance_lang),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_language_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.BENGALI,
                            onClick = { onSelectLanguage(AppLanguage.BENGALI) },
                            label = { Text(stringResource(R.string.language_option_bengali)) }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                            label = { Text(stringResource(R.string.language_option_english)) }
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_theme_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.SYSTEM,
                            onClick = { onSelectTheme(AppThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.LIGHT,
                            onClick = { onSelectTheme(AppThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == AppThemeMode.DARK,
                            onClick = { onSelectTheme(AppThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) }
                        )
                    }

                    SettingToggleRow(
                        label = stringResource(R.string.settings_dynamic_color_label),
                        checked = settings.dynamicColorEnabled,
                        onCheckedChange = onToggleDynamicColor
                    )
                }
            }
        }

        // Smart Readiness & Automation
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_automation),
                        style = MaterialTheme.typography.titleMedium
                    )
                    SettingToggleRow(
                        label = stringResource(R.string.settings_low_battery_toggle),
                        checked = settings.lowBatteryWarningEnabled,
                        onCheckedChange = onToggleLowBatteryAlert
                    )
                    if (settings.lowBatteryWarningEnabled) {
                        Text(
                            text = stringResource(
                                R.string.settings_low_battery_threshold,
                                settings.lowBatteryThresholdPercent
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.lowBatteryThresholdPercent.toFloat(),
                            onValueChange = { onChangeLowBatteryThreshold(it.toInt().coerceIn(5, 50)) },
                            valueRange = 5f..50f
                        )
                    }
                    SettingToggleRow(
                        label = stringResource(R.string.settings_charging_prompt_toggle),
                        checked = settings.chargingPromptEnabled,
                        onCheckedChange = onToggleChargingPrompt
                    )
                    SettingToggleRow(
                        label = stringResource(R.string.settings_reboot_check_toggle),
                        checked = settings.rebootRecoveryCheckEnabled,
                        onCheckedChange = onToggleRebootCheck
                    )
                    SettingToggleRow(
                        label = stringResource(R.string.settings_direct_ussd_toggle),
                        checked = settings.preferDirectUssdWhenPermitted,
                        onCheckedChange = onTogglePreferDirectUssd
                    )
                    Text(
                        text = stringResource(R.string.settings_direct_ussd_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Backup, Import & Redacted Diagnostics
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_backup),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_import_safety_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                    ) {
                        FilledTonalButton(
                            onClick = { exportBackupLauncher.launch("callora-backup.json") }
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_export_backup))
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*")) }
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_import_backup))
                        }
                        OutlinedButton(
                            onClick = { exportDiagLauncher.launch("callora-diagnostics-redacted.json") }
                        ) {
                            Text(stringResource(R.string.settings_export_diagnostics))
                        }
                    }
                }
            }
        }

        // Permissions, Privacy & About
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_legal),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.permissions_title))
                    }
                    OutlinedButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.privacy_title))
                    }
                    OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.about_title))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = CalloraSpacing.sm)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Screen 16: Privacy Policy Screen.
 */
@Composable
fun PrivacyPolicyScreen(
    modifier: Modifier = Modifier
) {
    val paragraphs = listOf(
        R.string.privacy_body_1,
        R.string.privacy_body_2,
        R.string.privacy_body_3,
        R.string.privacy_body_4,
        R.string.privacy_body_5
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
    ) {
        item {
            Text(
                text = stringResource(R.string.privacy_summary_header),
                style = MaterialTheme.typography.titleLarge
            )
        }
        items(paragraphs.size) { index ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(paragraphs[index]),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(CalloraSpacing.lg)
                )
            }
        }
    }
}

/**
 * Screen 17: About Callora Screen.
 * Displays exact verified developer identity (Shohan Khan / helloiamshohan@gmail.com)
 * and actual runtime application version without fabricating any entity or URL.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    appVersionName: String,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contactEmail = stringResource(R.string.about_contact_email)
    val appTitle = stringResource(R.string.app_name)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                ) {
                    Text(
                        text = appTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    InfoRow(
                        label = stringResource(R.string.about_version_label),
                        value = appVersionName,
                        monospace = true
                    )
                    InfoRow(
                        label = stringResource(R.string.about_developer_label),
                        value = stringResource(R.string.about_developer_value)
                    )
                    InfoRow(
                        label = stringResource(R.string.about_contact_label),
                        value = contactEmail
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                        Button(
                            onClick = {
                                val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$contactEmail")
                                    putExtra(Intent.EXTRA_SUBJECT, "$appTitle ($appVersionName) Feedback")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(mailIntent)
                                } catch (_: Exception) {
                                    // Ignore if no email client is installed
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.about_send_feedback))
                        }

                        OutlinedButton(onClick = onOpenPrivacyPolicy) {
                            Text(stringResource(R.string.privacy_title))
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.about_limitations_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.about_limitations_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.about_licenses_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.about_licenses_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
