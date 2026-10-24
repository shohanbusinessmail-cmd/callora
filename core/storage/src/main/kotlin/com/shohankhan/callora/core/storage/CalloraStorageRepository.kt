package com.shohankhan.callora.core.storage

import com.shohankhan.callora.core.common.BackupJsonCodec
import com.shohankhan.callora.core.common.BuiltInProfileCatalog
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.model.BackupPayload
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.SmartProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

interface PayloadCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

object PassthroughTestCipher : PayloadCipher {
    override fun encrypt(plainText: String): String = plainText
    override fun decrypt(cipherText: String): String = cipherText
}

interface CalloraStorageRepository {
    val settings: Flow<AppSettings>
    val profiles: Flow<List<SmartProfile>>
    val favorites: Flow<List<FavoriteDestination>>
    val observedStates: Flow<List<ObservedSimForwardingState>>
    val baselines: Flow<List<BaselineSimConfig>>
    val history: Flow<List<ConfigurationHistoryEntry>>

    suspend fun ensureSeeded()
    suspend fun currentSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)

    suspend fun upsertProfile(profile: SmartProfile)
    suspend fun duplicateProfile(profileId: String, copySuffix: String = "Copy"): SmartProfile?
    suspend fun deleteProfile(profileId: String)
    suspend fun setActiveProfile(profileId: String?, verificationState: ForwardingVerificationState)

    suspend fun upsertFavorite(favorite: FavoriteDestination)
    suspend fun deleteFavorite(favoriteId: String)

    suspend fun upsertObservedState(state: ObservedSimForwardingState)
    suspend fun invalidateObservedStatesForChangedSims(validSimSignatures: Set<String>)

    suspend fun upsertBaseline(baseline: BaselineSimConfig)
    suspend fun baselinesForSim(simSignature: String): List<BaselineSimConfig>

    suspend fun appendHistory(entry: ConfigurationHistoryEntry)
    suspend fun clearHistory()

    suspend fun exportBackupJson(appVersion: String): String
    suspend fun importBackupJson(rawJson: String): BackupJsonCodec.ImportValidationResult
}

/**
 * Pure Kotlin deterministic storage repository for fast unit, lifecycle, and Compose tests.
 */
class InMemoryCalloraStorageRepository(
    private val clock: CalloraClock = CalloraClock.SYSTEM,
    initialSettings: AppSettings = AppSettings()
) : CalloraStorageRepository {

    private val _settings = MutableStateFlow(initialSettings)
    private val _profiles = MutableStateFlow<List<SmartProfile>>(emptyList())
    private val _favorites = MutableStateFlow<List<FavoriteDestination>>(emptyList())
    private val _observedStates = MutableStateFlow<List<ObservedSimForwardingState>>(emptyList())
    private val _baselines = MutableStateFlow<List<BaselineSimConfig>>(emptyList())
    private val _history = MutableStateFlow<List<ConfigurationHistoryEntry>>(emptyList())

    override val settings: Flow<AppSettings> = _settings.asStateFlow()
    override val profiles: Flow<List<SmartProfile>> = _profiles.asStateFlow()
    override val favorites: Flow<List<FavoriteDestination>> = _favorites.asStateFlow()
    override val observedStates: Flow<List<ObservedSimForwardingState>> = _observedStates.asStateFlow()
    override val baselines: Flow<List<BaselineSimConfig>> = _baselines.asStateFlow()
    override val history: Flow<List<ConfigurationHistoryEntry>> = _history.asStateFlow()

    override suspend fun ensureSeeded() {
        if (_profiles.value.isEmpty()) {
            _profiles.value = BuiltInProfileCatalog.defaultBuiltInProfiles(clock.nowMillis())
        }
    }

    override suspend fun currentSettings(): AppSettings = _settings.value

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
    }

    override suspend fun upsertProfile(profile: SmartProfile) {
        val stamped = profile.copy(updatedAtMillis = clock.nowMillis())
        _profiles.update { list ->
            val exists = list.any { it.id == stamped.id }
            if (exists) list.map { if (it.id == stamped.id) stamped else it } else list + stamped
        }
    }

    override suspend fun duplicateProfile(profileId: String, copySuffix: String): SmartProfile? {
        val original = _profiles.value.firstOrNull { it.id == profileId } ?: return null
        val duplicated = original.copy(
            id = "custom_${UUID.randomUUID()}",
            name = "${original.name} ($copySuffix)".take(75),
            isBuiltIn = false,
            isSelectedActive = false,
            lastActivationAttemptMillis = null,
            lastVerifiedState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
            updatedAtMillis = clock.nowMillis()
        )
        upsertProfile(duplicated)
        return duplicated
    }

    override suspend fun deleteProfile(profileId: String) {
        _profiles.update { list ->
            list.filterNot { !it.isBuiltIn && it.id == profileId }
        }
    }

    override suspend fun setActiveProfile(profileId: String?, verificationState: ForwardingVerificationState) {
        val now = clock.nowMillis()
        _profiles.update { list ->
            list.map { p ->
                if (p.id == profileId) {
                    p.copy(
                        isSelectedActive = true,
                        lastActivationAttemptMillis = now,
                        lastVerifiedState = verificationState,
                        updatedAtMillis = now
                    )
                } else {
                    p.copy(isSelectedActive = false)
                }
            }
        }
    }

    override suspend fun upsertFavorite(favorite: FavoriteDestination) {
        _favorites.update { list ->
            val exists = list.any { it.id == favorite.id }
            if (exists) list.map { if (it.id == favorite.id) favorite else it } else listOf(favorite) + list
        }
    }

    override suspend fun deleteFavorite(favoriteId: String) {
        _favorites.update { list -> list.filterNot { it.id == favoriteId } }
    }

    override suspend fun upsertObservedState(state: ObservedSimForwardingState) {
        _observedStates.update { list ->
            val filtered = list.filterNot {
                it.simSignature == state.simSignature && it.serviceType == state.serviceType
            }
            filtered + state
        }
    }

    override suspend fun invalidateObservedStatesForChangedSims(validSimSignatures: Set<String>) {
        _observedStates.update { list ->
            list.map { state ->
                if (state.simSignature in validSimSignatures) {
                    state
                } else {
                    state.copy(
                        verificationState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                        observedEnabled = null,
                        observedDestinationMasked = null,
                        diagnosticNote = "SIM_SIGNATURE_CHANGED"
                    )
                }
            }
        }
    }

    override suspend fun upsertBaseline(baseline: BaselineSimConfig) {
        _baselines.update { list ->
            val filtered = list.filterNot {
                it.simSignature == baseline.simSignature && it.serviceType == baseline.serviceType
            }
            filtered + baseline
        }
    }

    override suspend fun baselinesForSim(simSignature: String): List<BaselineSimConfig> =
        _baselines.value.filter { it.simSignature == simSignature }

    override suspend fun appendHistory(entry: ConfigurationHistoryEntry) {
        _history.update { list -> (listOf(entry) + list).take(250) }
    }

    override suspend fun clearHistory() {
        _history.value = emptyList()
    }

    override suspend fun exportBackupJson(appVersion: String): String {
        val payload = BackupPayload(
            exportedAtMillis = clock.nowMillis(),
            appVersion = appVersion,
            settings = _settings.value,
            profiles = _profiles.value,
            favorites = _favorites.value,
            baselines = _baselines.value
        )
        return BackupJsonCodec.encodeBackup(payload)
    }

    override suspend fun importBackupJson(rawJson: String): BackupJsonCodec.ImportValidationResult {
        val result = BackupJsonCodec.decodeAndValidateBackup(rawJson)
        if (result is BackupJsonCodec.ImportValidationResult.Valid) {
            val p = result.payload
            _settings.update { current ->
                p.settings.copy(
                    onboardingCompleted = current.onboardingCompleted || p.settings.onboardingCompleted,
                    lastKnownSimSignatures = current.lastKnownSimSignatures,
                    pendingRebootReview = false
                )
            }
            // Merge profiles safely without auto-activating or executing any network commands
            val mergedProfiles = LinkedHashMap<String, SmartProfile>()
            for (existing in _profiles.value) mergedProfiles[existing.id] = existing.copy(isSelectedActive = false)
            for (imported in p.profiles) mergedProfiles[imported.id] = imported.copy(isSelectedActive = false)
            _profiles.value = mergedProfiles.values.toList()

            val mergedFavs = LinkedHashMap<String, FavoriteDestination>()
            for (existing in _favorites.value) mergedFavs[existing.id] = existing
            for (imported in p.favorites) mergedFavs[imported.id] = imported
            _favorites.value = mergedFavs.values.toList()

            val mergedBaselines = LinkedHashMap<Pair<String, ForwardingServiceType>, BaselineSimConfig>()
            for (existing in _baselines.value) mergedBaselines[existing.simSignature to existing.serviceType] = existing
            for (imported in p.baselines) mergedBaselines[imported.simSignature to imported.serviceType] = imported
            _baselines.value = mergedBaselines.values.toList()
        }
        return result
    }
}
