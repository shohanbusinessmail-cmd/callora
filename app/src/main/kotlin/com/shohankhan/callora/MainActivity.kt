package com.shohankhan.callora

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shohankhan.callora.core.common.BackupJsonCodec
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.ui.CalloraTheme
import com.shohankhan.callora.core.ui.ForwardingConfirmationDialog
import com.shohankhan.callora.core.ui.ForwardingProgressDialog
import com.shohankhan.callora.core.ui.ForwardingResultDialog
import com.shohankhan.callora.core.ui.R
import com.shohankhan.callora.feature.dashboard.DashboardScreen
import com.shohankhan.callora.feature.help.TroubleshootingScreen
import com.shohankhan.callora.feature.history.HistoryScreen
import com.shohankhan.callora.feature.profiles.ProfileEditorScreen
import com.shohankhan.callora.feature.profiles.ProfileListScreen
import com.shohankhan.callora.feature.profiles.ScheduleEditorScreen
import com.shohankhan.callora.feature.settings.AboutScreen
import com.shohankhan.callora.feature.settings.PrivacyPolicyScreen
import com.shohankhan.callora.feature.settings.SettingsScreen
import com.shohankhan.callora.feature.sims.FavoriteNumbersScreen
import com.shohankhan.callora.feature.sims.PermissionsCompatibilityScreen
import com.shohankhan.callora.feature.sims.SimManagementScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CalloraApplication
        setContent {
            val vm: CalloraAppViewModel = viewModel(
                factory = CalloraAppViewModel.factory(app.container)
            )
            CalloraAppRoot(
                viewModel = vm,
                appVersionName = BuildConfig.VERSION_NAME
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions and subscriptions cleanly when returning from system dialer or settings
    }
}

private enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Outlined.Dashboard),
    SIMS("sims", R.string.nav_sims, Icons.Outlined.SimCard),
    PROFILES("profiles", R.string.nav_profiles, Icons.Outlined.Tune),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History),
    HELP("help", R.string.nav_help, Icons.AutoMirrored.Outlined.HelpOutline)
}

private object SubRoutes {
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val PERMISSIONS = "permissions"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val PROFILE_EDITOR_NEW = "profile_editor_new"
    const val PROFILE_EDITOR = "profile_editor/{profileId}"
    const val SCHEDULE_EDITOR = "schedule_editor/{profileId}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalloraAppRoot(
    viewModel: CalloraAppViewModel,
    appVersionName: String
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val observedStates by viewModel.observedStates.collectAsStateWithLifecycle()
    val baselines by viewModel.baselines.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val simSnapshot by viewModel.simSnapshot.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsStateWithLifecycle()
    val pendingOp by viewModel.pendingOperation.collectAsStateWithLifecycle()
    val inProgress by viewModel.operationInProgress.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastOperationResult.collectAsStateWithLifecycle()
    val restorationPlan by viewModel.restorationPlan.collectAsStateWithLifecycle()
    val settingsBanner by viewModel.settingsBannerMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionsAndSubscriptions() }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionsAndSubscriptions() }

    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionsAndSubscriptions() }

    CalloraTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColorEnabled
    ) {
        if (!settings.onboardingCompleted) {
            OnboardingAndLanguageScreen(
                selectedLanguage = settings.language,
                onSelectLanguage = viewModel::selectLanguage,
                onFinishOnboarding = viewModel::completeOnboarding
            )
            return@CalloraTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: TopDestination.DASHBOARD.route
        val isTopLevel = TopDestination.entries.any { it.route == currentRoute }

        val config = LocalConfiguration.current
        val useNavRail = config.screenWidthDp >= 600

        val isBengali = settings.language == AppLanguage.BENGALI

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val titleRes = when {
                            currentRoute == SubRoutes.SETTINGS -> R.string.settings_title
                            currentRoute == SubRoutes.FAVORITES -> R.string.favorites_title
                            currentRoute == SubRoutes.PERMISSIONS -> R.string.permissions_title
                            currentRoute == SubRoutes.PRIVACY -> R.string.privacy_title
                            currentRoute == SubRoutes.ABOUT -> R.string.about_title
                            currentRoute.startsWith("profile_editor") -> R.string.profiles_title
                            currentRoute.startsWith("schedule_editor") -> R.string.schedule_editor_title
                            else -> R.string.app_name
                        }
                        Text(stringResource(titleRes))
                    },
                    navigationIcon = {
                        if (!isTopLevel) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_navigate_back)
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val next = if (isBengali) AppLanguage.ENGLISH else AppLanguage.BENGALI
                                viewModel.selectLanguage(next)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = stringResource(R.string.cd_change_language)
                            )
                            Text(text = if (isBengali) " EN" else " বাংলা")
                        }
                        if (currentRoute != SubRoutes.SETTINGS) {
                            IconButton(onClick = { navController.navigate(SubRoutes.SETTINGS) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.cd_open_settings)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail && isTopLevel) {
                    NavigationBar {
                        TopDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && isTopLevel) {
                    NavigationRail {
                        TopDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TopDestination.DASHBOARD.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(TopDestination.DASHBOARD.route) {
                        DashboardScreen(
                            settings = settings,
                            simSnapshot = simSnapshot,
                            battery = battery,
                            activeProfile = profiles.firstOrNull { it.isSelectedActive },
                            observedStates = observedStates,
                            recentHistory = history,
                            restorationPlanPreview = restorationPlan,
                            isBengali = isBengali,
                            onRequestPhonePermission = {
                                phonePermLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            },
                            onRefreshSims = viewModel::refreshPermissionsAndSubscriptions,
                            onOpenSimDetail = { slot ->
                                viewModel.selectSimSlot(slot)
                                navController.navigate(TopDestination.SIMS.route)
                            },
                            onPrepareUnreachableQuick = {
                                navController.navigate(TopDestination.SIMS.route)
                            },
                            onCheckStatusQuick = {
                                val sim = simSnapshot.subscriptions.firstOrNull()
                                if (sim != null) {
                                    viewModel.stageForwardingOperation(
                                        sim = sim,
                                        serviceType = ForwardingServiceType.UNREACHABLE,
                                        actionType = ForwardingActionType.QUERY_STATUS,
                                        destinationNumber = null,
                                        noReplyTimeoutSeconds = null
                                    )
                                } else {
                                    navController.navigate(TopDestination.SIMS.route)
                                }
                            },
                            onOpenRestoreDialog = viewModel::openSafeRestorationPlan,
                            onConfirmRestoreStep = viewModel::confirmRestorationStep,
                            onDismissRestoreDialog = viewModel::dismissRestorationPlan,
                            onAcknowledgeRebootReview = viewModel::acknowledgeRebootOrSimWarning,
                            onOpenFavorites = { navController.navigate(SubRoutes.FAVORITES) },
                            onOpenProfiles = { navController.navigate(TopDestination.PROFILES.route) },
                            onOpenHistory = { navController.navigate(TopDestination.HISTORY.route) },
                            onOpenTroubleshooting = { navController.navigate(TopDestination.HELP.route) },
                            onOpenCallSettings = viewModel::openSystemCallSettings
                        )
                    }

                    composable(TopDestination.SIMS.route) {
                        SimManagementScreen(
                            simSnapshot = simSnapshot,
                            selectedSlotIndex = selectedSimSlot,
                            observedStates = observedStates,
                            baselines = baselines,
                            favorites = favorites,
                            defaultCountryIso = settings.defaultCountryIso,
                            isBengali = isBengali,
                            onSelectSlot = viewModel::selectSimSlot,
                            onRequestForwardingAction = { sim, st, act, dest, timeout ->
                                viewModel.stageForwardingOperation(sim, st, act, dest, timeout)
                            },
                            onSaveBaseline = viewModel::saveBaseline,
                            onOpenCallSettings = viewModel::openSystemCallSettings,
                            onRequestPhonePermission = {
                                phonePermLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            },
                            onOpenFavoritesScreen = { navController.navigate(SubRoutes.FAVORITES) }
                        )
                    }

                    composable(TopDestination.PROFILES.route) {
                        ProfileListScreen(
                            profiles = profiles,
                            subscriptions = simSnapshot.subscriptions,
                            onCreateNew = { navController.navigate(SubRoutes.PROFILE_EDITOR_NEW) },
                            onEditProfile = { id -> navController.navigate("profile_editor/$id") },
                            onEditSchedule = { id -> navController.navigate("schedule_editor/$id") },
                            onDuplicateProfile = viewModel::duplicateProfile,
                            onDeleteProfile = viewModel::deleteProfile,
                            onActivateProfileOnSim = viewModel::stageProfileActivation,
                            onClearActiveProfile = viewModel::clearActiveProfileSelection
                        )
                    }

                    composable(SubRoutes.PROFILE_EDITOR_NEW) {
                        ProfileEditorScreen(
                            existingProfile = null,
                            subscriptions = simSnapshot.subscriptions,
                            defaultCountryIso = settings.defaultCountryIso,
                            onSave = { saved ->
                                viewModel.saveProfile(saved)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SubRoutes.PROFILE_EDITOR,
                        arguments = listOf(navArgument("profileId") { type = NavType.StringType })
                    ) { entry ->
                        val pid = entry.arguments?.getString("profileId")
                        val existing = profiles.firstOrNull { it.id == pid }
                        ProfileEditorScreen(
                            existingProfile = existing,
                            subscriptions = simSnapshot.subscriptions,
                            defaultCountryIso = settings.defaultCountryIso,
                            onSave = { saved ->
                                viewModel.saveProfile(saved)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SubRoutes.SCHEDULE_EDITOR,
                        arguments = listOf(navArgument("profileId") { type = NavType.StringType })
                    ) { entry ->
                        val pid = entry.arguments?.getString("profileId")
                        val existing = profiles.firstOrNull { it.id == pid }
                        if (existing != null) {
                            ScheduleEditorScreen(
                                profile = existing,
                                onSaveSchedule = { sched ->
                                    viewModel.updateProfileSchedule(existing, sched)
                                    navController.popBackStack()
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(TopDestination.HISTORY.route) {
                        HistoryScreen(
                            entries = history,
                            onClearHistory = viewModel::clearHistory
                        )
                    }

                    composable(TopDestination.HELP.route) {
                        TroubleshootingScreen(
                            isBengali = isBengali,
                            onOpenCallSettings = viewModel::openSystemCallSettings
                        )
                    }

                    composable(SubRoutes.FAVORITES) {
                        FavoriteNumbersScreen(
                            favorites = favorites,
                            defaultCountryIso = settings.defaultCountryIso,
                            onAddFavorite = viewModel::addFavorite,
                            onDeleteFavorite = viewModel::deleteFavorite
                        )
                    }

                    composable(SubRoutes.PERMISSIONS) {
                        PermissionsCompatibilityScreen(
                            permissions = permissions,
                            onRequestReadPhoneState = {
                                phonePermLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestCallPhone = {
                                callPermLauncher.launch(Manifest.permission.CALL_PHONE)
                            },
                            onOpenSystemAppSettings = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }

                    composable(SubRoutes.SETTINGS) {
                        val exportOkMsg = stringResource(R.string.settings_backup_success)
                        val importOkMsg = stringResource(R.string.settings_import_success)
                        val importFailFmt = stringResource(R.string.settings_import_failed, "%s")

                        SettingsScreen(
                            settings = settings,
                            statusBannerMessage = settingsBanner,
                            onSelectLanguage = viewModel::selectLanguage,
                            onSelectTheme = viewModel::selectTheme,
                            onToggleDynamicColor = viewModel::setDynamicColor,
                            onToggleLowBatteryAlert = viewModel::setLowBatteryWarning,
                            onChangeLowBatteryThreshold = viewModel::setLowBatteryThreshold,
                            onToggleChargingPrompt = viewModel::setChargingPrompt,
                            onToggleRebootCheck = viewModel::setRebootRecoveryCheck,
                            onTogglePreferDirectUssd = viewModel::setPreferDirectUssd,
                            onExportBackupToUri = { uri ->
                                scope.launch {
                                    val json = viewModel.generateBackupJson(appVersionName)
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            out.write(json.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    viewModel.postSettingsBanner(exportOkMsg)
                                }
                            },
                            onImportBackupFromUri = { uri ->
                                scope.launch {
                                    val raw = withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                                    }
                                    when (val res = viewModel.importBackupJson(raw)) {
                                        is BackupJsonCodec.ImportValidationResult.Valid ->
                                            viewModel.postSettingsBanner(importOkMsg)
                                        is BackupJsonCodec.ImportValidationResult.Invalid ->
                                            viewModel.postSettingsBanner(importFailFmt.replace("%s", res.errorCode))
                                    }
                                }
                            },
                            onExportDiagnosticsToUri = { uri ->
                                scope.launch {
                                    val json = viewModel.generateRedactedDiagnosticsJson(appVersionName)
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            out.write(json.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    viewModel.postSettingsBanner(exportOkMsg)
                                }
                            },
                            onOpenPermissions = { navController.navigate(SubRoutes.PERMISSIONS) },
                            onOpenPrivacyPolicy = { navController.navigate(SubRoutes.PRIVACY) },
                            onOpenAbout = { navController.navigate(SubRoutes.ABOUT) }
                        )
                    }

                    composable(SubRoutes.PRIVACY) {
                        PrivacyPolicyScreen()
                    }

                    composable(SubRoutes.ABOUT) {
                        AboutScreen(
                            appVersionName = appVersionName,
                            onOpenPrivacyPolicy = { navController.navigate(SubRoutes.PRIVACY) }
                        )
                    }
                }
            }
        }

        // Screen 7: Explicit Confirmation Dialog
        pendingOp?.let { pending ->
            ForwardingConfirmationDialog(
                model = pending.confirmationModel,
                onConfirm = viewModel::executeConfirmedOperation,
                onDismiss = viewModel::cancelPendingOperation
            )
        }

        // Screen 8: Progress State
        if (inProgress) {
            ForwardingProgressDialog()
        }

        // Screen 9: Truthful Operation Result & Verification Details
        lastResult?.let { result ->
            ForwardingResultDialog(
                result = result,
                onCopyMmi = viewModel::copyMmi,
                onOpenCallSettings = viewModel::openSystemCallSettings,
                onCheckStatusNow = {
                    val sim = simSnapshot.subscriptions.firstOrNull { it.simSlotIndex == selectedSimSlot }
                        ?: simSnapshot.subscriptions.firstOrNull()
                    viewModel.dismissOperationResult()
                    if (sim != null) {
                        viewModel.stageForwardingOperation(
                            sim = sim,
                            serviceType = ForwardingServiceType.UNREACHABLE,
                            actionType = ForwardingActionType.QUERY_STATUS,
                            destinationNumber = null,
                            noReplyTimeoutSeconds = null
                        )
                    }
                },
                onDismiss = viewModel::dismissOperationResult
            )
        }
    }
}
