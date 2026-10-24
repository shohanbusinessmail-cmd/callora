package com.shohankhan.callora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.ui.CalloraSpacing
import com.shohankhan.callora.core.ui.HeroGradientBanner
import com.shohankhan.callora.core.ui.R

/**
 * Screens 1 & 2: First-Launch Language Selection & Honest Technical Onboarding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingAndLanguageScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalloraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CalloraSpacing.lg)
        ) {
            HeroGradientBanner(
                title = stringResource(R.string.app_name),
                body = stringResource(R.string.app_tagline)
            )

            if (step == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(CalloraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_choose_language_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.onboarding_choose_language_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)) {
                            FilterChip(
                                selected = selectedLanguage == AppLanguage.BENGALI,
                                onClick = { onSelectLanguage(AppLanguage.BENGALI) },
                                label = { Text(stringResource(R.string.language_option_bengali)) }
                            )
                            FilterChip(
                                selected = selectedLanguage == AppLanguage.ENGLISH,
                                onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                                label = { Text(stringResource(R.string.language_option_english)) }
                            )
                        }
                    }
                }
            } else {
                val (titleRes, bodyRes) = when (step) {
                    1 -> R.string.onboarding_step1_title to R.string.onboarding_step1_body
                    2 -> R.string.onboarding_step2_title to R.string.onboarding_step2_body
                    else -> R.string.onboarding_step3_title to R.string.onboarding_step3_body
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(CalloraSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CalloraSpacing.md)
                    ) {
                        Text(
                            text = "$step / 3",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(bodyRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(Modifier.height(CalloraSpacing.sm))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CalloraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CalloraSpacing.sm)
            ) {
                if (step < 3) {
                    Button(onClick = { step++ }) {
                        Text(stringResource(R.string.action_continue))
                    }
                    OutlinedButton(onClick = onFinishOnboarding) {
                        Text(stringResource(R.string.action_skip))
                    }
                } else {
                    Button(onClick = onFinishOnboarding) {
                        Text(stringResource(R.string.action_get_started))
                    }
                }
            }
        }
    }
}
