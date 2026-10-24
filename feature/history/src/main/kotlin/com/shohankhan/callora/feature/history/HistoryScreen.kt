package com.shohankhan.callora.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.core.ui.VerificationStatusBadge
import com.shohankhan.callora.core.ui.labelRes
import java.text.DateFormat
import java.util.Date

/**
 * Screen 10: Local Configuration History Screen.
 * Explicitly discloses that only Callora configuration requests are recorded (never call logs).
 */
@Composable
fun HistoryScreen(
    entries: List<ConfigurationHistoryEntry>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.history_disclaimer),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (entries.isNotEmpty()) {
                        OutlinedButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.history_clear_button))
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(CalloraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${entry.simDisplayLabel} (${entry.carrierName})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(entry.timestampMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${entry.serviceType.acronym} • ${stringResource(entry.actionType.labelRes())}" +
                                (entry.requestedDestinationMasked?.let { " → $it" }.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!entry.profileName.isNullOrBlank()) {
                            Text(
                                text = entry.profileName!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        VerificationStatusBadge(state = entry.verificationState)
                        if (!entry.failureReasonCode.isNullOrBlank()) {
                            Text(
                                text = entry.failureReasonCode!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
