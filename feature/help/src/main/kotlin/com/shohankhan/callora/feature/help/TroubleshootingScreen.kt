package com.shohankhan.callora.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsPhone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shohankhan.callora.core.telephony.BangladeshCarrierCatalog
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.core.ui.badgeRes

/**
 * Screen 14: Troubleshooting Center & Bangladesh Carrier Knowledge Base.
 */
@Composable
fun TroubleshootingScreen(
    isBengali: Boolean,
    onOpenCallSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = listOf(
        R.string.ts_topic_phone_off_title to R.string.ts_topic_phone_off_body,
        R.string.ts_topic_unsupported_op_title to R.string.ts_topic_unsupported_op_body,
        R.string.ts_topic_invalid_dest_title to R.string.ts_topic_invalid_dest_body,
        R.string.ts_topic_wrong_sim_title to R.string.ts_topic_wrong_sim_body,
        R.string.ts_topic_network_vo_wifi_title to R.string.ts_topic_network_vo_wifi_body,
        R.string.ts_topic_perm_denied_title to R.string.ts_topic_perm_denied_body,
        R.string.ts_topic_ussd_failed_title to R.string.ts_topic_ussd_failed_body,
        R.string.ts_topic_unverified_title to R.string.ts_topic_unverified_body,
        R.string.ts_topic_sim_swap_title to R.string.ts_topic_sim_swap_body,
        R.string.ts_topic_reboot_unexpected_title to R.string.ts_topic_reboot_unexpected_body
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CalloraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
    ) {
        item {
            Text(
                text = stringResource(R.string.troubleshooting_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenCallSettings,
                modifier = Modifier.padding(top = CalloraSpacing.sm)
            ) {
                Icon(Icons.Outlined.SettingsPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_open_call_settings))
            }
        }

        items(topics.size) { index ->
            val (titleRes, bodyRes) = topics[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bangladesh Carrier Evidence Cards
        items(BangladeshCarrierCatalog.allBangladeshOperators, key = { it.operatorId }) { carrier ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(CalloraSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CalloraSpacing.xs)
                ) {
                    Text(
                        text = "${if (isBengali) carrier.displayNameBn else carrier.displayNameEn} (${carrier.mccMncCodes.joinToString()})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(carrier.verificationLevel.badgeRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (isBengali) carrier.tariffWarningBn else carrier.tariffWarningEn,
                        style = MaterialTheme.typography.bodySmall
                    )
                    val restrictions = if (isBengali) carrier.knownRestrictionsBn else carrier.knownRestrictionsEn
                    restrictions.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${carrier.verificationSourceTitle} (${carrier.lastVerifiedIsoDate})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
