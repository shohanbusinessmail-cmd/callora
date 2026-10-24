package com.shohankhan.callora.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.shohankhan.callora.core.common.BackupJsonCodec
import com.shohankhan.callora.core.common.BuiltInProfileCatalog
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.model.BackupPayload
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.BuiltInProfileType
import com.shohankhan.callora.core.model.ConfigurationHistoryEntry
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingActionType
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingResultCode
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.ObservedSimForwardingState
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.SimForwardingRule
import com.shohankhan.callora.core.model.SmartProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.calloraDataStore: DataStore<Preferences> by preferencesDataStore(name = "callora_settings")

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val builtInType: String,
    val isBuiltIn: Boolean,
    val scheduleEnabled: Boolean,
    val scheduleDaysCsv: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val expiresAtEpochMillis: Long?,
    val lowBatteryThresholdPercent: Int?,
    val suggestWhenChargingStarts: Boolean,
    val requiresConfirmation: Boolean,
    val isSelectedActive: Boolean,
    val lastActivationAttemptMillis: Long?,
    val lastVerifiedState: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "profile_rules", primaryKeys = ["profileId", "simSlotIndex", "serviceType"])
data class ProfileRuleEntity(
    val profileId: String,
    val simSignature: String,
    val simSlotIndex: Int,
    val simDisplayLabel: String,
    val enabled: Boolean,
    val serviceType: String,
    val encryptedDestination: String,
    val noReplyTimeoutSeconds: Int
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val label: String,
    val encryptedNormalizedNumber: String,
    val encryptedOriginalInput: String,
    val notes: String,
    val createdAtMillis: Long
)

@Entity(tableName = "observed_states", primaryKeys = ["simSignature", "serviceType"])
data class ObservedStateEntity(
    val simSignature: String,
    val simSlotIndex: Int,
    val serviceType: String,
    val verificationState: String,
    val observedEnabled: Boolean?,
    val observedDestinationMasked: String?,
    val desiredEnabled: Boolean?,
    val desiredDestinationMasked: String?,
    val lastCheckedAtMillis: Long?,
    val lastResultCode: String?,
    val diagnosticNote: String?
)

@Entity(tableName = "baselines", primaryKeys = ["simSignature", "serviceType"])
data class BaselineEntity(
    val simSignature: String,
    val simSlotIndex: Int,
    val serviceType: String,
    val shouldBeEnabled: Boolean,
    val encryptedBaselineDestination: String?,
    val updatedAtMillis: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val simSlotIndex: Int,
    val simDisplayLabel: String,
    val carrierName: String,
    val serviceType: String,
    val actionType: String,
    val previousDestinationMasked: String?,
    val requestedDestinationMasked: String?,
    val executionMethod: String,
    val resultCode: String,
    val verificationState: String,
    val failureReasonCode: String?,
    val profileName: String?
)

@Dao
interface CalloraDao {
    @Query("SELECT * FROM profiles ORDER BY isBuiltIn DESC, updatedAtMillis DESC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile_rules")
    fun observeProfileRules(): Flow<List<ProfileRuleEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun profileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(entity: ProfileEntity)

    @Query("DELETE FROM profile_rules WHERE profileId = :profileId")
    suspend fun deleteRulesForProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<ProfileRuleEntity>)

    @Transaction
    suspend fun replaceProfileWithRules(profile: ProfileEntity, rules: List<ProfileRuleEntity>) {
        insertProfile(profile)
        deleteRulesForProfile(profile.id)
        if (rules.isNotEmpty()) insertRules(rules)
    }

    @Query("DELETE FROM profiles WHERE id = :profileId AND isBuiltIn = 0")
    suspend fun deleteCustomProfile(profileId: String)

    @Query("UPDATE profiles SET isSelectedActive = 0")
    suspend fun clearAllActiveProfiles()

    @Query("UPDATE profiles SET isSelectedActive = 1, lastActivationAttemptMillis = :now, lastVerifiedState = :state, updatedAtMillis = :now WHERE id = :profileId")
    suspend fun markProfileActive(profileId: String, state: String, now: Long)

    @Query("SELECT * FROM favorites ORDER BY createdAtMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :favoriteId")
    suspend fun deleteFavorite(favoriteId: String)

    @Query("SELECT * FROM observed_states")
    fun observeStates(): Flow<List<ObservedStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservedState(entity: ObservedStateEntity)

    @Query("SELECT * FROM observed_states")
    suspend fun getAllObservedStates(): List<ObservedStateEntity>

    @Query("SELECT * FROM baselines")
    fun observeBaselines(): Flow<List<BaselineEntity>>

    @Query("SELECT * FROM baselines WHERE simSignature = :simSignature")
    suspend fun getBaselinesForSim(simSignature: String): List<BaselineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(entity: BaselineEntity)

    @Query("SELECT * FROM history ORDER BY timestampMillis DESC LIMIT 250")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Database(
    entities = [
        ProfileEntity::class,
        ProfileRuleEntity::class,
        FavoriteEntity::class,
        ObservedStateEntity::class,
        BaselineEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CalloraDatabase : RoomDatabase() {
    abstract fun dao(): CalloraDao

    companion object {
        fun build(context: Context): CalloraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CalloraDatabase::class.java,
                "callora.db"
            ).fallbackToDestructiveMigration().build()
    }
}

class RoomCalloraStorageRepository(
    context: Context,
    private val database: CalloraDatabase,
    private val cipher: PayloadCipher = AndroidKeyStoreCipher(),
    private val clock: CalloraClock = CalloraClock.SYSTEM
) : CalloraStorageRepository {

    private val dataStore = context.applicationContext.calloraDataStore
    private val dao = database.dao()

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_COUNTRY = stringPreferencesKey("default_country")
        val LOW_BATTERY_ENABLED = booleanPreferencesKey("low_battery_enabled")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val CHARGING_PROMPT = booleanPreferencesKey("charging_prompt")
        val REBOOT_RECOVERY = booleanPreferencesKey("reboot_recovery")
        val PREFER_DIRECT_USSD = booleanPreferencesKey("prefer_direct_ussd")
        val PENDING_REBOOT_REVIEW = booleanPreferencesKey("pending_reboot_review")
        val LAST_KNOWN_SIMS = stringSetPreferencesKey("last_known_sim_signatures")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                AppThemeMode.entries.firstOrNull { it.name == name }
            } ?: AppThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultCountryIso = prefs[Keys.DEFAULT_COUNTRY] ?: "BD",
            lowBatteryWarningEnabled = prefs[Keys.LOW_BATTERY_ENABLED] ?: true,
            lowBatteryThresholdPercent = (prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20).coerceIn(5, 50),
            chargingPromptEnabled = prefs[Keys.CHARGING_PROMPT] ?: false,
            rebootRecoveryCheckEnabled = prefs[Keys.REBOOT_RECOVERY] ?: true,
            preferDirectUssdWhenPermitted = prefs[Keys.PREFER_DIRECT_USSD] ?: false,
            pendingRebootReview = prefs[Keys.PENDING_REBOOT_REVIEW] ?: false,
            lastKnownSimSignatures = prefs[Keys.LAST_KNOWN_SIMS] ?: emptySet()
        )
    }

    override val profiles: Flow<List<SmartProfile>> = combine(
        dao.observeProfiles(),
        dao.observeProfileRules()
    ) { profileEntities, ruleEntities ->
        val rulesByProfile = ruleEntities.groupBy { it.profileId }
        profileEntities.map { pe ->
            pe.toDomain(rulesByProfile[pe.id].orEmpty(), cipher)
        }
    }

    override val favorites: Flow<List<FavoriteDestination>> = dao.observeFavorites().map { list ->
        list.map { fe ->
            FavoriteDestination(
                id = fe.id,
                label = fe.label,
                normalizedNumber = cipher.decrypt(fe.encryptedNormalizedNumber),
                originalInput = cipher.decrypt(fe.encryptedOriginalInput),
                notes = fe.notes,
                createdAtMillis = fe.createdAtMillis
            )
        }
    }

    override val observedStates: Flow<List<ObservedSimForwardingState>> = dao.observeStates().map { list ->
        list.map { it.toDomain() }
    }

    override val baselines: Flow<List<BaselineSimConfig>> = dao.observeBaselines().map { list ->
        list.map { it.toDomain(cipher) }
    }

    override val history: Flow<List<ConfigurationHistoryEntry>> = dao.observeHistory().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun ensureSeeded() {
        if (dao.profileCount() == 0) {
            val defaults = BuiltInProfileCatalog.defaultBuiltInProfiles(clock.nowMillis())
            for (p in defaults) {
                upsertProfile(p)
            }
        }
    }

    override suspend fun currentSettings(): AppSettings = settings.first()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = settings.first()
            val updated = transform(current)
            prefs[Keys.ONBOARDING_COMPLETED] = updated.onboardingCompleted
            prefs[Keys.LANGUAGE] = updated.language.languageTag
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColorEnabled
            prefs[Keys.DEFAULT_COUNTRY] = updated.defaultCountryIso
            prefs[Keys.LOW_BATTERY_ENABLED] = updated.lowBatteryWarningEnabled
            prefs[Keys.LOW_BATTERY_THRESHOLD] = updated.lowBatteryThresholdPercent.coerceIn(5, 50)
            prefs[Keys.CHARGING_PROMPT] = updated.chargingPromptEnabled
            prefs[Keys.REBOOT_RECOVERY] = updated.rebootRecoveryCheckEnabled
            prefs[Keys.PREFER_DIRECT_USSD] = updated.preferDirectUssdWhenPermitted
            prefs[Keys.PENDING_REBOOT_REVIEW] = updated.pendingRebootReview
            prefs[Keys.LAST_KNOWN_SIMS] = updated.lastKnownSimSignatures
        }
    }

    override suspend fun upsertProfile(profile: SmartProfile) {
        val stamped = profile.copy(updatedAtMillis = clock.nowMillis())
        val pe = ProfileEntity(
            id = stamped.id,
            name = stamped.name,
            description = stamped.description,
            builtInType = stamped.builtInType.name,
            isBuiltIn = stamped.isBuiltIn,
            scheduleEnabled = stamped.schedule.enabled,
            scheduleDaysCsv = stamped.schedule.daysOfWeek.sorted().joinToString(","),
            startMinuteOfDay = stamped.schedule.startMinuteOfDay,
            endMinuteOfDay = stamped.schedule.endMinuteOfDay,
            expiresAtEpochMillis = stamped.schedule.expiresAtEpochMillis,
            lowBatteryThresholdPercent = stamped.lowBatteryThresholdPercent,
            suggestWhenChargingStarts = stamped.suggestWhenChargingStarts,
            requiresConfirmation = stamped.requiresConfirmation,
            isSelectedActive = stamped.isSelectedActive,
            lastActivationAttemptMillis = stamped.lastActivationAttemptMillis,
            lastVerifiedState = stamped.lastVerifiedState.name,
            updatedAtMillis = stamped.updatedAtMillis
        )
        val rules = stamped.rules.map { r ->
            ProfileRuleEntity(
                profileId = stamped.id,
                simSignature = r.simSignature,
                simSlotIndex = r.simSlotIndex,
                simDisplayLabel = r.simDisplayLabel,
                enabled = r.enabled,
                serviceType = r.serviceType.name,
                encryptedDestination = cipher.encrypt(r.destinationNumber),
                noReplyTimeoutSeconds = r.noReplyTimeoutSeconds
            )
        }
        dao.replaceProfileWithRules(pe, rules)
    }

    override suspend fun duplicateProfile(profileId: String, copySuffix: String): SmartProfile? {
        val original = profiles.first().firstOrNull { it.id == profileId } ?: return null
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
        dao.deleteRulesForProfile(profileId)
        dao.deleteCustomProfile(profileId)
    }

    override suspend fun setActiveProfile(profileId: String?, verificationState: ForwardingVerificationState) {
        dao.clearAllActiveProfiles()
        if (profileId != null) {
            dao.markProfileActive(profileId, verificationState.name, clock.nowMillis())
        }
    }

    override suspend fun upsertFavorite(favorite: FavoriteDestination) {
        dao.insertFavorite(
            FavoriteEntity(
                id = favorite.id,
                label = favorite.label,
                encryptedNormalizedNumber = cipher.encrypt(favorite.normalizedNumber),
                encryptedOriginalInput = cipher.encrypt(favorite.originalInput),
                notes = favorite.notes,
                createdAtMillis = favorite.createdAtMillis
            )
        )
    }

    override suspend fun deleteFavorite(favoriteId: String) {
        dao.deleteFavorite(favoriteId)
    }

    override suspend fun upsertObservedState(state: ObservedSimForwardingState) {
        dao.insertObservedState(
            ObservedStateEntity(
                simSignature = state.simSignature,
                simSlotIndex = state.simSlotIndex,
                serviceType = state.serviceType.name,
                verificationState = state.verificationState.name,
                observedEnabled = state.observedEnabled,
                observedDestinationMasked = state.observedDestinationMasked,
                desiredEnabled = state.desiredEnabled,
                desiredDestinationMasked = state.desiredDestinationMasked,
                lastCheckedAtMillis = state.lastCheckedAtMillis,
                lastResultCode = state.lastResultCode?.name,
                diagnosticNote = state.diagnosticNote
            )
        )
    }

    override suspend fun invalidateObservedStatesForChangedSims(validSimSignatures: Set<String>) {
        val all = dao.getAllObservedStates()
        for (entity in all) {
            if (entity.simSignature !in validSimSignatures) {
                dao.insertObservedState(
                    entity.copy(
                        verificationState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE.name,
                        observedEnabled = null,
                        observedDestinationMasked = null,
                        diagnosticNote = "SIM_SIGNATURE_CHANGED"
                    )
                )
            }
        }
    }

    override suspend fun upsertBaseline(baseline: BaselineSimConfig) {
        dao.insertBaseline(
            BaselineEntity(
                simSignature = baseline.simSignature,
                simSlotIndex = baseline.simSlotIndex,
                serviceType = baseline.serviceType.name,
                shouldBeEnabled = baseline.shouldBeEnabled,
                encryptedBaselineDestination = baseline.baselineDestinationNumber?.let { cipher.encrypt(it) },
                updatedAtMillis = baseline.updatedAtMillis
            )
        )
    }

    override suspend fun baselinesForSim(simSignature: String): List<BaselineSimConfig> =
        dao.getBaselinesForSim(simSignature).map { it.toDomain(cipher) }

    override suspend fun appendHistory(entry: ConfigurationHistoryEntry) {
        dao.insertHistory(
            HistoryEntity(
                id = entry.id,
                timestampMillis = entry.timestampMillis,
                simSlotIndex = entry.simSlotIndex,
                simDisplayLabel = entry.simDisplayLabel,
                carrierName = entry.carrierName,
                serviceType = entry.serviceType.name,
                actionType = entry.actionType.name,
                previousDestinationMasked = entry.previousDestinationMasked,
                requestedDestinationMasked = entry.requestedDestinationMasked,
                executionMethod = entry.executionMethod.name,
                resultCode = entry.resultCode.name,
                verificationState = entry.verificationState.name,
                failureReasonCode = entry.failureReasonCode,
                profileName = entry.profileName
            )
        )
    }

    override suspend fun clearHistory() {
        dao.clearHistory()
    }

    override suspend fun exportBackupJson(appVersion: String): String {
        val payload = BackupPayload(
            exportedAtMillis = clock.nowMillis(),
            appVersion = appVersion,
            settings = currentSettings(),
            profiles = profiles.first(),
            favorites = favorites.first(),
            baselines = baselines.first()
        )
        return BackupJsonCodec.encodeBackup(payload)
    }

    override suspend fun importBackupJson(rawJson: String): BackupJsonCodec.ImportValidationResult {
        val result = BackupJsonCodec.decodeAndValidateBackup(rawJson)
        if (result is BackupJsonCodec.ImportValidationResult.Valid) {
            val payload = result.payload
            updateSettings { current ->
                payload.settings.copy(
                    onboardingCompleted = current.onboardingCompleted || payload.settings.onboardingCompleted,
                    lastKnownSimSignatures = current.lastKnownSimSignatures,
                    pendingRebootReview = false
                )
            }
            dao.clearAllActiveProfiles()
            for (profile in payload.profiles) {
                upsertProfile(profile.copy(isSelectedActive = false))
            }
            for (favorite in payload.favorites) {
                upsertFavorite(favorite)
            }
            for (baseline in payload.baselines) {
                upsertBaseline(baseline)
            }
        }
        return result
    }
}

private fun ProfileEntity.toDomain(rules: List<ProfileRuleEntity>, cipher: PayloadCipher): SmartProfile {
    val days = scheduleDaysCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    return SmartProfile(
        id = id,
        name = name,
        description = description,
        builtInType = BuiltInProfileType.entries.firstOrNull { it.name == builtInType } ?: BuiltInProfileType.CUSTOM,
        isBuiltIn = isBuiltIn,
        rules = rules.map { r ->
            SimForwardingRule(
                simSignature = r.simSignature,
                simSlotIndex = r.simSlotIndex,
                simDisplayLabel = r.simDisplayLabel,
                enabled = r.enabled,
                serviceType = ForwardingServiceType.entries.firstOrNull { it.name == r.serviceType }
                    ?: ForwardingServiceType.UNREACHABLE,
                destinationNumber = cipher.decrypt(r.encryptedDestination),
                noReplyTimeoutSeconds = r.noReplyTimeoutSeconds
            )
        },
        schedule = ProfileSchedule(
            enabled = scheduleEnabled,
            daysOfWeek = days.ifEmpty { setOf(1, 2, 3, 4, 5) },
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            expiresAtEpochMillis = expiresAtEpochMillis
        ),
        lowBatteryThresholdPercent = lowBatteryThresholdPercent,
        suggestWhenChargingStarts = suggestWhenChargingStarts,
        requiresConfirmation = requiresConfirmation,
        isSelectedActive = isSelectedActive,
        lastActivationAttemptMillis = lastActivationAttemptMillis,
        lastVerifiedState = ForwardingVerificationState.entries.firstOrNull { it.name == lastVerifiedState }
            ?: ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
        updatedAtMillis = updatedAtMillis
    )
}

private fun ObservedStateEntity.toDomain(): ObservedSimForwardingState = ObservedSimForwardingState(
    simSignature = simSignature,
    simSlotIndex = simSlotIndex,
    serviceType = ForwardingServiceType.entries.firstOrNull { it.name == serviceType }
        ?: ForwardingServiceType.UNREACHABLE,
    verificationState = ForwardingVerificationState.entries.firstOrNull { it.name == verificationState }
        ?: ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
    observedEnabled = observedEnabled,
    observedDestinationMasked = observedDestinationMasked,
    desiredEnabled = desiredEnabled,
    desiredDestinationMasked = desiredDestinationMasked,
    lastCheckedAtMillis = lastCheckedAtMillis,
    lastResultCode = lastResultCode?.let { rc -> ForwardingResultCode.entries.firstOrNull { it.name == rc } },
    diagnosticNote = diagnosticNote
)

private fun BaselineEntity.toDomain(cipher: PayloadCipher): BaselineSimConfig = BaselineSimConfig(
    simSignature = simSignature,
    simSlotIndex = simSlotIndex,
    serviceType = ForwardingServiceType.entries.firstOrNull { it.name == serviceType }
        ?: ForwardingServiceType.UNREACHABLE,
    shouldBeEnabled = shouldBeEnabled,
    baselineDestinationNumber = encryptedBaselineDestination?.let { cipher.decrypt(it) },
    updatedAtMillis = updatedAtMillis
)

private fun HistoryEntity.toDomain(): ConfigurationHistoryEntry = ConfigurationHistoryEntry(
    id = id,
    timestampMillis = timestampMillis,
    simSlotIndex = simSlotIndex,
    simDisplayLabel = simDisplayLabel,
    carrierName = carrierName,
    serviceType = ForwardingServiceType.entries.firstOrNull { it.name == serviceType }
        ?: ForwardingServiceType.UNREACHABLE,
    actionType = ForwardingActionType.entries.firstOrNull { it.name == actionType }
        ?: ForwardingActionType.ACTIVATE,
    previousDestinationMasked = previousDestinationMasked,
    requestedDestinationMasked = requestedDestinationMasked,
    executionMethod = ForwardingExecutionMethod.entries.firstOrNull { it.name == executionMethod }
        ?: ForwardingExecutionMethod.SAFE_DIALER_INTENT,
    resultCode = ForwardingResultCode.entries.firstOrNull { it.name == resultCode }
        ?: ForwardingResultCode.FAILED,
    verificationState = ForwardingVerificationState.entries.firstOrNull { it.name == verificationState }
        ?: ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
    failureReasonCode = failureReasonCode,
    profileName = profileName
)
