package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.AppLanguage
import com.shohankhan.callora.core.model.AppSettings
import com.shohankhan.callora.core.model.AppThemeMode
import com.shohankhan.callora.core.model.BackupPayload
import com.shohankhan.callora.core.model.BaselineSimConfig
import com.shohankhan.callora.core.model.BuiltInProfileType
import com.shohankhan.callora.core.model.FavoriteDestination
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.ForwardingVerificationState
import com.shohankhan.callora.core.model.PhoneNumberValidationResult
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.SimForwardingRule
import com.shohankhan.callora.core.model.SmartProfile

/**
 * Documented, strict RFC-8259 JSON export/import codec for Callora configuration backups
 * and redacted diagnostic reports.
 *
 * Security invariants:
 * - Validates schemaVersion, value types, numeric bounds, and phone number syntax before accepting import.
 * - Rejects malformed JSON, unsupported schema versions, or MMI injection characters in destinations.
 * - Importing a backup NEVER automatically executes network forwarding commands; all imported profiles
 *   reset `isSelectedActive = false` and `lastVerifiedState = UNKNOWN_NETWORK_STATE`.
 */
object BackupJsonCodec {

    const val FORMAT_IDENTIFIER = "callora-backup-v1"

    sealed interface ImportValidationResult {
        data class Valid(val payload: BackupPayload, val warnings: List<String>) : ImportValidationResult
        data class Invalid(val errorCode: String, val detail: String) : ImportValidationResult
    }

    fun encodeBackup(payload: BackupPayload): String {
        val root = linkedMapOf<String, JsonVal>(
            "format" to JsonVal.Str(FORMAT_IDENTIFIER),
            "schemaVersion" to JsonVal.Num(payload.schemaVersion.toDouble()),
            "exportedAtMillis" to JsonVal.Num(payload.exportedAtMillis.toDouble()),
            "appVersion" to JsonVal.Str(payload.appVersion),
            "settings" to JsonVal.Obj(
                linkedMapOf(
                    "language" to JsonVal.Str(payload.settings.language.languageTag),
                    "themeMode" to JsonVal.Str(payload.settings.themeMode.name),
                    "dynamicColorEnabled" to JsonVal.Bool(payload.settings.dynamicColorEnabled),
                    "defaultCountryIso" to JsonVal.Str(payload.settings.defaultCountryIso),
                    "lowBatteryWarningEnabled" to JsonVal.Bool(payload.settings.lowBatteryWarningEnabled),
                    "lowBatteryThresholdPercent" to JsonVal.Num(payload.settings.lowBatteryThresholdPercent.toDouble()),
                    "chargingPromptEnabled" to JsonVal.Bool(payload.settings.chargingPromptEnabled),
                    "rebootRecoveryCheckEnabled" to JsonVal.Bool(payload.settings.rebootRecoveryCheckEnabled)
                )
            ),
            "profiles" to JsonVal.Arr(payload.profiles.map { encodeProfile(it) }),
            "favorites" to JsonVal.Arr(payload.favorites.map { encodeFavorite(it) }),
            "baselines" to JsonVal.Arr(payload.baselines.map { encodeBaseline(it) })
        )
        return JsonWriter.stringify(JsonVal.Obj(root), indent = 2)
    }

    fun decodeAndValidateBackup(rawJson: String): ImportValidationResult {
        if (rawJson.isBlank()) {
            return ImportValidationResult.Invalid("EMPTY_FILE", "Backup content is empty.")
        }
        if (rawJson.length > 1_048_576) {
            return ImportValidationResult.Invalid("FILE_TOO_LARGE", "Backup exceeds 1 MB safety limit.")
        }

        val parsed = try {
            JsonParser(rawJson).parseRoot()
        } catch (e: JsonParseException) {
            return ImportValidationResult.Invalid("MALFORMED_JSON", e.message ?: "Invalid JSON syntax.")
        }

        val root = parsed as? JsonVal.Obj
            ?: return ImportValidationResult.Invalid("INVALID_ROOT", "Expected JSON object at root.")

        val format = root.string("format")
        if (format != FORMAT_IDENTIFIER) {
            return ImportValidationResult.Invalid(
                "UNSUPPORTED_FORMAT",
                "Expected format '$FORMAT_IDENTIFIER', got '${format ?: "null"}'."
            )
        }

        val schemaVersion = root.int("schemaVersion")
        if (schemaVersion != BackupPayload.CURRENT_SCHEMA_VERSION) {
            return ImportValidationResult.Invalid(
                "UNSUPPORTED_SCHEMA_VERSION",
                "Unsupported schemaVersion: $schemaVersion."
            )
        }

        val exportedAtMillis = root.long("exportedAtMillis") ?: 0L
        val appVersion = root.string("appVersion") ?: "unknown"

        val settingsObj = root.obj("settings")
            ?: return ImportValidationResult.Invalid("MISSING_SETTINGS", "Missing 'settings' object.")

        val threshold = settingsObj.int("lowBatteryThresholdPercent") ?: 20
        if (threshold !in 5..50) {
            return ImportValidationResult.Invalid(
                "INVALID_BATTERY_THRESHOLD",
                "lowBatteryThresholdPercent must be between 5 and 50."
            )
        }

        val themeMode = settingsObj.string("themeMode")?.let { name ->
            AppThemeMode.entries.firstOrNull { it.name == name }
        } ?: return ImportValidationResult.Invalid("INVALID_THEME_MODE", "Unknown themeMode.")

        val defaultCountry = (settingsObj.string("defaultCountryIso") ?: "BD").uppercase()
        if (defaultCountry.length != 2 || defaultCountry.any { it !in 'A'..'Z' }) {
            return ImportValidationResult.Invalid("INVALID_COUNTRY_ISO", "Invalid 2-letter ISO country code.")
        }

        val settings = AppSettings(
            onboardingCompleted = true,
            language = AppLanguage.fromTag(settingsObj.string("language")),
            themeMode = themeMode,
            dynamicColorEnabled = settingsObj.bool("dynamicColorEnabled") ?: false,
            defaultCountryIso = defaultCountry,
            lowBatteryWarningEnabled = settingsObj.bool("lowBatteryWarningEnabled") ?: true,
            lowBatteryThresholdPercent = threshold,
            chargingPromptEnabled = settingsObj.bool("chargingPromptEnabled") ?: false,
            rebootRecoveryCheckEnabled = settingsObj.bool("rebootRecoveryCheckEnabled") ?: true,
            preferDirectUssdWhenPermitted = false,
            pendingRebootReview = false
        )

        val warnings = mutableListOf<String>()

        val profilesArr = root.arr("profiles") ?: emptyList()
        val profiles = mutableListOf<SmartProfile>()
        for ((idx, item) in profilesArr.withIndex()) {
            val obj = item as? JsonVal.Obj
                ?: return ImportValidationResult.Invalid("INVALID_PROFILE", "Profile[$idx] is not an object.")
            val id = obj.string("id")?.trim().orEmpty()
            val name = obj.string("name")?.trim().orEmpty()
            if (id.isEmpty() || name.isEmpty() || name.length > 80) {
                return ImportValidationResult.Invalid("INVALID_PROFILE_METADATA", "Profile[$idx] has invalid id or name.")
            }
            val builtInType = obj.string("builtInType")?.let { t ->
                BuiltInProfileType.entries.firstOrNull { it.name == t }
            } ?: BuiltInProfileType.CUSTOM

            val rulesArr = obj.arr("rules") ?: emptyList()
            val rules = mutableListOf<SimForwardingRule>()
            for ((rIdx, rItem) in rulesArr.withIndex()) {
                val rObj = rItem as? JsonVal.Obj
                    ?: return ImportValidationResult.Invalid("INVALID_RULE", "Profile[$idx].rules[$rIdx] invalid.")
                val serviceType = rObj.string("serviceType")?.let { st ->
                    ForwardingServiceType.entries.firstOrNull { it.name == st }
                } ?: return ImportValidationResult.Invalid("INVALID_SERVICE_TYPE", "Unknown serviceType in Profile[$idx].")
                val dest = rObj.string("destinationNumber").orEmpty()
                val enabled = rObj.bool("enabled") ?: false
                if (enabled || dest.isNotBlank()) {
                    val v = PhoneNumberValidator.validate(dest, defaultCountryIso = defaultCountry)
                    if (v !is PhoneNumberValidationResult.Valid) {
                        return ImportValidationResult.Invalid(
                            "INVALID_RULE_DESTINATION",
                            "Profile '$name' contains an invalid forwarding number."
                        )
                    }
                }
                rules.add(
                    SimForwardingRule(
                        simSignature = rObj.string("simSignature").orEmpty(),
                        simSlotIndex = (rObj.int("simSlotIndex") ?: 0).coerceIn(0, 7),
                        simDisplayLabel = rObj.string("simDisplayLabel").orEmpty().take(60),
                        enabled = enabled,
                        serviceType = serviceType,
                        destinationNumber = dest,
                        noReplyTimeoutSeconds = GsmMmiCodec.normalizeNoReplyTimeout(rObj.int("noReplyTimeoutSeconds"))
                    )
                )
            }

            val schedObj = obj.obj("schedule")
            val schedule = if (schedObj != null) {
                val days = (schedObj.arr("daysOfWeek") ?: emptyList()).mapNotNull { (it as? JsonVal.Num)?.value?.toInt() }.toSet()
                val candidate = ProfileSchedule(
                    enabled = schedObj.bool("enabled") ?: false,
                    daysOfWeek = days.ifEmpty { setOf(1, 2, 3, 4, 5) },
                    startMinuteOfDay = schedObj.int("startMinuteOfDay") ?: 540,
                    endMinuteOfDay = schedObj.int("endMinuteOfDay") ?: 1080,
                    expiresAtEpochMillis = schedObj.long("expiresAtEpochMillis")
                )
                if (candidate.enabled && days.any { it !in 1..7 }) {
                    return ImportValidationResult.Invalid("INVALID_SCHEDULE", "Schedule days must be 1..7.")
                }
                if (candidate.startMinuteOfDay !in 0..1439 || candidate.endMinuteOfDay !in 0..1439) {
                    return ImportValidationResult.Invalid("INVALID_SCHEDULE", "Schedule minutes must be 0..1439.")
                }
                candidate
            } else {
                ProfileSchedule()
            }

            // Safety guarantee: imported profiles are NEVER auto-activated or marked verified.
            profiles.add(
                SmartProfile(
                    id = id,
                    name = name,
                    description = obj.string("description").orEmpty().take(240),
                    builtInType = builtInType,
                    isBuiltIn = obj.bool("isBuiltIn") ?: false,
                    rules = rules,
                    schedule = schedule,
                    lowBatteryThresholdPercent = obj.int("lowBatteryThresholdPercent")?.coerceIn(5, 50),
                    suggestWhenChargingStarts = obj.bool("suggestWhenChargingStarts") ?: false,
                    requiresConfirmation = true,
                    isSelectedActive = false,
                    lastActivationAttemptMillis = null,
                    lastVerifiedState = ForwardingVerificationState.UNKNOWN_NETWORK_STATE,
                    updatedAtMillis = obj.long("updatedAtMillis") ?: exportedAtMillis
                )
            )
        }

        val favoritesArr = root.arr("favorites") ?: emptyList()
        val favorites = mutableListOf<FavoriteDestination>()
        for ((fIdx, fItem) in favoritesArr.withIndex()) {
            val fObj = fItem as? JsonVal.Obj
                ?: return ImportValidationResult.Invalid("INVALID_FAVORITE", "Favorite[$fIdx] invalid.")
            val label = fObj.string("label")?.trim().orEmpty()
            val number = fObj.string("normalizedNumber")?.trim().orEmpty()
            val validNum = PhoneNumberValidator.validate(number, defaultCountryIso = defaultCountry)
            if (label.isEmpty() || validNum !is PhoneNumberValidationResult.Valid) {
                return ImportValidationResult.Invalid("INVALID_FAVORITE_NUMBER", "Favorite[$fIdx] has invalid number or label.")
            }
            favorites.add(
                FavoriteDestination(
                    id = fObj.string("id")?.ifBlank { "fav-$fIdx" } ?: "fav-$fIdx",
                    label = label.take(60),
                    normalizedNumber = validNum.e164Normalized,
                    originalInput = fObj.string("originalInput") ?: validNum.displayFormatted,
                    notes = fObj.string("notes").orEmpty().take(160),
                    createdAtMillis = fObj.long("createdAtMillis") ?: exportedAtMillis
                )
            )
        }

        val baselinesArr = root.arr("baselines") ?: emptyList()
        val baselines = mutableListOf<BaselineSimConfig>()
        for ((bIdx, bItem) in baselinesArr.withIndex()) {
            val bObj = bItem as? JsonVal.Obj
                ?: return ImportValidationResult.Invalid("INVALID_BASELINE", "Baseline[$bIdx] invalid.")
            val serviceType = bObj.string("serviceType")?.let { st ->
                ForwardingServiceType.entries.firstOrNull { it.name == st }
            } ?: return ImportValidationResult.Invalid("INVALID_BASELINE_SERVICE", "Baseline[$bIdx] invalid service.")
            val shouldEnable = bObj.bool("shouldBeEnabled") ?: false
            val dest = bObj.string("baselineDestinationNumber")
            if (shouldEnable) {
                val validDest = PhoneNumberValidator.validate(dest.orEmpty(), defaultCountryIso = defaultCountry)
                if (validDest !is PhoneNumberValidationResult.Valid) {
                    return ImportValidationResult.Invalid("INVALID_BASELINE_NUMBER", "Baseline[$bIdx] requires valid destination.")
                }
            }
            baselines.add(
                BaselineSimConfig(
                    simSignature = bObj.string("simSignature").orEmpty(),
                    simSlotIndex = (bObj.int("simSlotIndex") ?: 0).coerceIn(0, 7),
                    serviceType = serviceType,
                    shouldBeEnabled = shouldEnable,
                    baselineDestinationNumber = dest,
                    updatedAtMillis = bObj.long("updatedAtMillis") ?: exportedAtMillis
                )
            )
        }

        warnings.add("IMPORT_DOES_NOT_EXECUTE_NETWORK_COMMANDS")
        return ImportValidationResult.Valid(
            payload = BackupPayload(
                schemaVersion = schemaVersion,
                exportedAtMillis = exportedAtMillis,
                appVersion = appVersion,
                settings = settings,
                profiles = profiles,
                favorites = favorites,
                baselines = baselines
            ),
            warnings = warnings
        )
    }

    fun buildRedactedDiagnosticJson(
        appVersion: String,
        sdkInt: Int,
        languageTag: String,
        activeSimCount: Int,
        carrierIds: List<String>,
        recentOutcomeCodes: List<String>,
        generatedAtMillis: Long
    ): String {
        val doc = JsonVal.Obj(
            linkedMapOf(
                "format" to JsonVal.Str("callora-diagnostics-redacted-v1"),
                "generatedAtMillis" to JsonVal.Num(generatedAtMillis.toDouble()),
                "appVersion" to JsonVal.Str(appVersion),
                "androidSdkInt" to JsonVal.Num(sdkInt.toDouble()),
                "languageTag" to JsonVal.Str(languageTag),
                "activeSimCount" to JsonVal.Num(activeSimCount.toDouble()),
                "detectedCarrierIds" to JsonVal.Arr(carrierIds.map { JsonVal.Str(it) }),
                "recentOperationResults" to JsonVal.Arr(recentOutcomeCodes.take(20).map { JsonVal.Str(SensitiveDataRedactor.redactFreeText(it)) }),
                "redactionPolicy" to JsonVal.Str("Phone numbers, ICCID, IMSI, and personal labels excluded by default.")
            )
        )
        return JsonWriter.stringify(doc, indent = 2)
    }

    private fun encodeProfile(p: SmartProfile): JsonVal.Obj = JsonVal.Obj(
        linkedMapOf(
            "id" to JsonVal.Str(p.id),
            "name" to JsonVal.Str(p.name),
            "description" to JsonVal.Str(p.description),
            "builtInType" to JsonVal.Str(p.builtInType.name),
            "isBuiltIn" to JsonVal.Bool(p.isBuiltIn),
            "lowBatteryThresholdPercent" to (p.lowBatteryThresholdPercent?.let { JsonVal.Num(it.toDouble()) } ?: JsonVal.Null),
            "suggestWhenChargingStarts" to JsonVal.Bool(p.suggestWhenChargingStarts),
            "requiresConfirmation" to JsonVal.Bool(true),
            "updatedAtMillis" to JsonVal.Num(p.updatedAtMillis.toDouble()),
            "schedule" to JsonVal.Obj(
                linkedMapOf(
                    "enabled" to JsonVal.Bool(p.schedule.enabled),
                    "daysOfWeek" to JsonVal.Arr(p.schedule.daysOfWeek.sorted().map { JsonVal.Num(it.toDouble()) }),
                    "startMinuteOfDay" to JsonVal.Num(p.schedule.startMinuteOfDay.toDouble()),
                    "endMinuteOfDay" to JsonVal.Num(p.schedule.endMinuteOfDay.toDouble()),
                    "expiresAtEpochMillis" to (p.schedule.expiresAtEpochMillis?.let { JsonVal.Num(it.toDouble()) } ?: JsonVal.Null)
                )
            ),
            "rules" to JsonVal.Arr(
                p.rules.map { r ->
                    JsonVal.Obj(
                        linkedMapOf(
                            "simSignature" to JsonVal.Str(r.simSignature),
                            "simSlotIndex" to JsonVal.Num(r.simSlotIndex.toDouble()),
                            "simDisplayLabel" to JsonVal.Str(r.simDisplayLabel),
                            "enabled" to JsonVal.Bool(r.enabled),
                            "serviceType" to JsonVal.Str(r.serviceType.name),
                            "destinationNumber" to JsonVal.Str(r.destinationNumber),
                            "noReplyTimeoutSeconds" to JsonVal.Num(r.noReplyTimeoutSeconds.toDouble())
                        )
                    )
                }
            )
        )
    )

    private fun encodeFavorite(f: FavoriteDestination): JsonVal.Obj = JsonVal.Obj(
        linkedMapOf(
            "id" to JsonVal.Str(f.id),
            "label" to JsonVal.Str(f.label),
            "normalizedNumber" to JsonVal.Str(f.normalizedNumber),
            "originalInput" to JsonVal.Str(f.originalInput),
            "notes" to JsonVal.Str(f.notes),
            "createdAtMillis" to JsonVal.Num(f.createdAtMillis.toDouble())
        )
    )

    private fun encodeBaseline(b: BaselineSimConfig): JsonVal.Obj = JsonVal.Obj(
        linkedMapOf(
            "simSignature" to JsonVal.Str(b.simSignature),
            "simSlotIndex" to JsonVal.Num(b.simSlotIndex.toDouble()),
            "serviceType" to JsonVal.Str(b.serviceType.name),
            "shouldBeEnabled" to JsonVal.Bool(b.shouldBeEnabled),
            "baselineDestinationNumber" to (b.baselineDestinationNumber?.let { JsonVal.Str(it) } ?: JsonVal.Null),
            "updatedAtMillis" to JsonVal.Num(b.updatedAtMillis.toDouble())
        )
    )
}

internal class JsonParseException(message: String) : Exception(message)

internal sealed interface JsonVal {
    data object Null : JsonVal
    data class Bool(val value: Boolean) : JsonVal
    data class Num(val value: Double) : JsonVal
    data class Str(val value: String) : JsonVal
    data class Arr(val items: List<JsonVal>) : JsonVal
    data class Obj(val entries: Map<String, JsonVal>) : JsonVal {
        fun string(key: String): String? = (entries[key] as? Str)?.value
        fun bool(key: String): Boolean? = (entries[key] as? Bool)?.value
        fun int(key: String): Int? = (entries[key] as? Num)?.value?.toInt()
        fun long(key: String): Long? = (entries[key] as? Num)?.value?.toLong()
        fun obj(key: String): Obj? = entries[key] as? Obj
        fun arr(key: String): List<JsonVal>? = (entries[key] as? Arr)?.items
    }
}

internal object JsonWriter {
    fun stringify(value: JsonVal, indent: Int = 2, depth: Int = 0): String {
        val pad = " ".repeat(indent * depth)
        val childPad = " ".repeat(indent * (depth + 1))
        return when (value) {
            JsonVal.Null -> "null"
            is JsonVal.Bool -> value.value.toString()
            is JsonVal.Num -> {
                val d = value.value
                if (d % 1.0 == 0.0 && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                    d.toLong().toString()
                } else {
                    d.toString()
                }
            }
            is JsonVal.Str -> escapeString(value.value)
            is JsonVal.Arr -> {
                if (value.items.isEmpty()) "[]"
                else value.items.joinToString(
                    separator = ",\n$childPad",
                    prefix = "[\n$childPad",
                    postfix = "\n$pad]"
                ) { stringify(it, indent, depth + 1) }
            }
            is JsonVal.Obj -> {
                if (value.entries.isEmpty()) "{}"
                else value.entries.entries.joinToString(
                    separator = ",\n$childPad",
                    prefix = "{\n$childPad",
                    postfix = "\n$pad}"
                ) { (k, v) -> "${escapeString(k)}: ${stringify(v, indent, depth + 1)}" }
            }
        }
    }

    private fun escapeString(s: String): String = buildString(s.length + 2) {
        append('"')
        for (ch in s) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u%04x".format(ch.code))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}

internal class JsonParser(private val src: String) {
    private var pos = 0

    fun parseRoot(): JsonVal {
        skipWs()
        val v = parseValue()
        skipWs()
        if (pos != src.length) throw JsonParseException("Unexpected trailing character at offset $pos")
        return v
    }

    private fun parseValue(): JsonVal {
        skipWs()
        if (pos >= src.length) throw JsonParseException("Unexpected end of JSON")
        return when (src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonVal.Str(parseString())
            't' -> { expectLiteral("true"); JsonVal.Bool(true) }
            'f' -> { expectLiteral("false"); JsonVal.Bool(false) }
            'n' -> { expectLiteral("null"); JsonVal.Null }
            '-', in '0'..'9' -> parseNumber()
            else -> throw JsonParseException("Unexpected character '${src[pos]}' at offset $pos")
        }
    }

    private fun parseObject(): JsonVal.Obj {
        pos++ // '{'
        skipWs()
        val map = LinkedHashMap<String, JsonVal>()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return JsonVal.Obj(map)
        }
        while (true) {
            skipWs()
            if (pos >= src.length || src[pos] != '"') {
                throw JsonParseException("Expected object key string at offset $pos")
            }
            val key = parseString()
            if (map.containsKey(key)) {
                throw JsonParseException("Duplicate JSON key '$key' at offset $pos")
            }
            skipWs()
            if (pos >= src.length || src[pos] != ':') {
                throw JsonParseException("Expected ':' after key '$key' at offset $pos")
            }
            pos++
            val value = parseValue()
            map[key] = value
            skipWs()
            if (pos >= src.length) throw JsonParseException("Unterminated object")
            when (src[pos]) {
                ',' -> pos++
                '}' -> { pos++; return JsonVal.Obj(map) }
                else -> throw JsonParseException("Expected ',' or '}' at offset $pos")
            }
        }
    }

    private fun parseArray(): JsonVal.Arr {
        pos++ // '['
        skipWs()
        val items = mutableListOf<JsonVal>()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return JsonVal.Arr(items)
        }
        while (true) {
            items.add(parseValue())
            skipWs()
            if (pos >= src.length) throw JsonParseException("Unterminated array")
            when (src[pos]) {
                ',' -> pos++
                ']' -> { pos++; return JsonVal.Arr(items) }
                else -> throw JsonParseException("Expected ',' or ']' at offset $pos")
            }
        }
    }

    private fun parseString(): String {
        pos++ // '"'
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= src.length) throw JsonParseException("Unterminated escape sequence")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > src.length) throw JsonParseException("Incomplete unicode escape")
                            val hex = src.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16)
                                ?: throw JsonParseException("Invalid unicode escape \\u$hex")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw JsonParseException("Invalid escape \\$esc")
                    }
                }
                else -> {
                    if (ch.code < 0x20) throw JsonParseException("Unescaped control character in string")
                    sb.append(ch)
                }
            }
        }
        throw JsonParseException("Unterminated string literal")
    }

    private fun parseNumber(): JsonVal.Num {
        val start = pos
        if (src[pos] == '-') pos++
        if (pos >= src.length || src[pos] !in '0'..'9') {
            throw JsonParseException("Invalid number at offset $start")
        }
        while (pos < src.length && src[pos] in '0'..'9') pos++
        if (pos < src.length && src[pos] == '.') {
            pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw JsonParseException("Invalid decimal fraction at offset $start")
            }
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw JsonParseException("Invalid exponent at offset $start")
            }
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        val token = src.substring(start, pos)
        val num = token.toDoubleOrNull() ?: throw JsonParseException("Unparseable number '$token'")
        if (!num.isFinite()) throw JsonParseException("Non-finite number '$token'")
        return JsonVal.Num(num)
    }

    private fun expectLiteral(literal: String) {
        if (!src.startsWith(literal, pos)) {
            throw JsonParseException("Expected '$literal' at offset $pos")
        }
        pos += literal.length
    }

    private fun skipWs() {
        while (pos < src.length) {
            when (src[pos]) {
                ' ', '\t', '\r', '\n' -> pos++
                else -> return
            }
        }
    }
}
