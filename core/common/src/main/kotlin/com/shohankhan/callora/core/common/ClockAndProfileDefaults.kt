package com.shohankhan.callora.core.common

import com.shohankhan.callora.core.model.BuiltInProfileType
import com.shohankhan.callora.core.model.ProfileSchedule
import com.shohankhan.callora.core.model.SmartProfile

fun interface CalloraClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = CalloraClock { System.currentTimeMillis() }
    }
}

object BuiltInProfileCatalog {

    fun defaultBuiltInProfiles(nowMillis: Long): List<SmartProfile> = listOf(
        SmartProfile(
            id = "builtin_phone_unreachable",
            name = "Phone Unreachable",
            description = "Configure carrier CFNRc (62) in advance so calls redirect when your phone is off or out of coverage.",
            builtInType = BuiltInProfileType.PHONE_UNREACHABLE,
            isBuiltIn = true,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_battery_emergency",
            name = "Battery Emergency",
            description = "Prepare unreachable call forwarding before critical battery depletion shuts down the device.",
            builtInType = BuiltInProfileType.BATTERY_EMERGENCY,
            isBuiltIn = true,
            lowBatteryThresholdPercent = 15,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_charging_mode",
            name = "Charging Mode",
            description = "Forward calls to a desk or secondary handset while your primary phone stays on a charger.",
            builtInType = BuiltInProfileType.CHARGING_MODE,
            isBuiltIn = true,
            suggestWhenChargingStarts = true,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_work_mode",
            name = "Work Mode",
            description = "Route unanswered or busy calls to your work line during scheduled office hours.",
            builtInType = BuiltInProfileType.WORK_MODE,
            isBuiltIn = true,
            schedule = ProfileSchedule(
                enabled = false,
                daysOfWeek = setOf(1, 2, 3, 4, 7), // Sun-Thu Bangladesh workweek!
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60
            ),
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_travel_mode",
            name = "Travel Mode",
            description = "Forward reachable/unreachable calls to a local Bangladesh SIM before international travel or weak-signal transit.",
            builtInType = BuiltInProfileType.TRAVEL_MODE,
            isBuiltIn = true,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_temporary",
            name = "Temporary Forwarding",
            description = "Time-boxed call diversion with an explicit reminder to restore your intended baseline.",
            builtInType = BuiltInProfileType.TEMPORARY_FORWARDING,
            isBuiltIn = true,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        ),
        SmartProfile(
            id = "builtin_custom_template",
            name = "Custom Mode",
            description = "Flexible per-SIM call forwarding rules tailored to your own workflow.",
            builtInType = BuiltInProfileType.CUSTOM,
            isBuiltIn = true,
            requiresConfirmation = true,
            updatedAtMillis = nowMillis
        )
    )
}
