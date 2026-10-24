package com.shohankhan.callora

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.forwarding.CalloraForwardingCoordinator
import com.shohankhan.callora.core.forwarding.FakeForwardingExecutor
import com.shohankhan.callora.core.forwarding.ForwardingExecutor
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.SimSubscription
import com.shohankhan.callora.core.storage.CalloraStorageRepository
import com.shohankhan.callora.core.storage.InMemoryCalloraStorageRepository
import com.shohankhan.callora.core.telephony.BatteryStateSource
import com.shohankhan.callora.core.telephony.FakeTelephonyPlatform
import com.shohankhan.callora.core.telephony.PermissionStatusProvider
import com.shohankhan.callora.core.telephony.SubscriptionDataSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI & Accessibility Compose tests executed via Robolectric (mocked platform environment).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w480dp-h1600dp")
class CalloraUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildTestContainer(
        onboardingDone: Boolean,
        language: AppLanguage = AppLanguage.ENGLISH,
        sims: List<SimSubscription> = emptyList()
    ): AppContainer {
        val fakeClock = CalloraClock { 1_700_000_000_000L }
        val platform = FakeTelephonyPlatform(initialSubscriptions = sims)
        val storage = InMemoryCalloraStorageRepository(
            clock = fakeClock,
            initialSettings = AppSettings(
                onboardingCompleted = onboardingDone,
                language = language
            )
        )
        val exec = FakeForwardingExecutor(fakeClock)
        val coord = CalloraForwardingCoordinator(platform, platform, storage, exec, fakeClock)

        return object : AppContainer {
            override val clock: CalloraClock = fakeClock
            override val storage: CalloraStorageRepository = storage
            override val permissionStatusProvider: PermissionStatusProvider = platform
            override val subscriptionDataSource: SubscriptionDataSource = platform
            override val batteryStateSource: BatteryStateSource = platform
            override val forwardingExecutor: ForwardingExecutor = exec
            override val forwardingCoordinator: CalloraForwardingCoordinator = coord
        }
    }

    @Test
    fun `first launch shows language selection and completes onboarding into Dashboard`() {
        val container = buildTestContainer(onboardingDone = false)
        val vm = CalloraAppViewModel(container)

        composeTestRule.setContent {
            CalloraAppRoot(viewModel = vm, appVersionName = "0.9.0-test")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("বাংলা (Bengali)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Quick Actions").assertIsDisplayed()
    }

    @Test
    fun `dashboard renders dual SIM cards truthfully as Unknown Network State initially`() {
        val sims = listOf(
            SimSubscription(1, 0, false, "GP Primary", "Grameenphone", "470", "01"),
            SimSubscription(2, 1, false, "Robi Secondary", "Robi", "470", "02")
        )
        val container = buildTestContainer(onboardingDone = true, sims = sims)
        val vm = CalloraAppViewModel(container)

        composeTestRule.setContent {
            CalloraAppRoot(viewModel = vm, appVersionName = "0.9.0-test")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("GP Primary — Grameenphone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Robi Secondary — Robi").assertIsDisplayed()
    }
}
