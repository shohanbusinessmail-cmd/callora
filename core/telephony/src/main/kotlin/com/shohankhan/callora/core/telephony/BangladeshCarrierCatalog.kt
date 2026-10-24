package com.shohankhan.callora.core.telephony

import com.shohankhan.callora.core.model.CarrierConfig
import com.shohankhan.callora.core.model.CarrierVerificationLevel
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingServiceType
import com.shohankhan.callora.core.model.SimSubscription

/**
 * Evidence-backed Bangladesh mobile operator configuration catalog.
 *
 * Strictly distinguishes:
 * - Officially published GSM code tables (Grameenphone),
 * - Official handset Call Settings procedures (Teletalk),
 * - Standard 3GPP TS 22.030 GSM MMI codes subject to plan/IMS provisioning (Robi, Airtel, Banglalink),
 * - Unverified unknown/foreign carriers (never auto-executes MMI codes).
 */
object BangladeshCarrierCatalog {

    const val OP_GRAMEENPHONE = "GRAMEENPHONE"
    const val OP_ROBI = "ROBI"
    const val OP_AIRTEL_BD = "AIRTEL_BD"
    const val OP_BANGLALINK = "BANGLALINK"
    const val OP_TELETALK = "TELETALK"
    const val OP_UNKNOWN = "UNKNOWN_GENERIC"

    private val STANDARD_ACTIVATION_TEMPLATES = mapOf(
        ForwardingServiceType.UNREACHABLE to "**62*{number}#",
        ForwardingServiceType.BUSY to "**67*{number}#",
        ForwardingServiceType.UNANSWERED to "**61*{number}#",
        ForwardingServiceType.UNCONDITIONAL to "**21*{number}#"
    )

    private val STANDARD_DEACTIVATION_TEMPLATES = mapOf(
        ForwardingServiceType.UNREACHABLE to "##62#",
        ForwardingServiceType.BUSY to "##67#",
        ForwardingServiceType.UNANSWERED to "##61#",
        ForwardingServiceType.UNCONDITIONAL to "##21#"
    )

    private val STANDARD_STATUS_TEMPLATES = mapOf(
        ForwardingServiceType.UNREACHABLE to "*#62#",
        ForwardingServiceType.BUSY to "*#67#",
        ForwardingServiceType.UNANSWERED to "*#61#",
        ForwardingServiceType.UNCONDITIONAL to "*#21#"
    )

    val grameenphone = CarrierConfig(
        operatorId = OP_GRAMEENPHONE,
        displayNameEn = "Grameenphone",
        displayNameBn = "গ্রামীণফোন",
        mccMncCodes = setOf("470-01"),
        originalBtrcPrefixes = setOf("017", "013"),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.OFFICIAL_CODE_TABLE_VERIFIED,
        preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
        allowMmiDialPreparation = true,
        activationMmiTemplates = STANDARD_ACTIVATION_TEMPLATES,
        deactivationMmiTemplates = STANDARD_DEACTIVATION_TEMPLATES,
        statusQueryMmiTemplates = STANDARD_STATUS_TEMPLATES,
        cancelAllMmiCode = "##002#",
        verificationSourceTitle = "Grameenphone Official Useful Information — GSM Feature Activation/Deactivation",
        verificationSourceUrl = "https://www.grameenphone.com/useful-information",
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = "121",
        tariffWarningEn = "Official GP terms state that FlexiPlan minutes, purchased minute packs, bonus minutes, special call-rate offers, and Emergency Balance minute loans do NOT apply to call divert/forward calls. Regular package tariff + VAT/SD/SC applies.",
        tariffWarningBn = "গ্রামীণফোনের অফিসিয়াল শর্তানুযায়ী ফ্লেক্সিপ্ল্যান মিনিট, কেনা মিনিট প্যাক, বোনাস মিনিট, স্পেশাল কলরেট ও ইমার্জেন্সি ব্যালেন্স মিনিট কল ডাইভার্ট/ফরওয়ার্ডে প্রযোজ্য নয়। নিয়মিত প্যাকেজ ট্যারিফ ও ভ্যাট/এসডি/এসসি প্রযোজ্য হবে।",
        knownRestrictionsEn = listOf(
            "Sufficient main account balance (prepaid) or credit limit (postpaid) is required for diverted calls.",
            "Call barring settings on the handset can block incoming or diverted calls.",
            "Prefix 017/013 does not guarantee Grameenphone if ported via MNP."
        ),
        knownRestrictionsBn = listOf(
            "ডাইভার্ট হওয়া কলের জন্য মূল অ্যাকাউন্টে পর্যাপ্ত ব্যালেন্স (প্রিপেইড) বা ক্রেডিট লিমিট (পোস্টপেইড) থাকতে হবে।",
            "হ্যান্ডসেটে কল ব্যারিং চালু থাকলে কল ফরওয়ার্ডিং বাধাগ্রস্ত হতে পারে।",
            "এমএনপি (MNP) করা নম্বরে ০১৭/০১৩ প্রিফিক্স মানেই গ্রামীণফোন নেটওয়ার্ক নিশ্চিত নয়।"
        ),
        manualInstructionsEn = "Open the Phone app, select your Grameenphone SIM, dial **62*DestinationNumber# for Unreachable Forwarding, or use Phone > Settings > Calling accounts > Call forwarding.",
        manualInstructionsBn = "ফোন ডায়ালারে গ্রামীণফোন সিম নির্বাচন করে আনরিচেবল ফরওয়ার্ডিংয়ের জন্য **62*গন্তব্য_নম্বর# ডায়াল করুন, অথবা Phone > Settings > Calling accounts > Call forwarding ব্যবহার করুন।"
    )

    val robi = CarrierConfig(
        operatorId = OP_ROBI,
        displayNameEn = "Robi",
        displayNameBn = "রবি",
        mccMncCodes = setOf("470-02"),
        originalBtrcPrefixes = setOf("018", "016"),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.STANDARD_3GPP_PROVISIONING_DEPENDENT,
        preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
        allowMmiDialPreparation = true,
        activationMmiTemplates = STANDARD_ACTIVATION_TEMPLATES,
        deactivationMmiTemplates = STANDARD_DEACTIVATION_TEMPLATES,
        statusQueryMmiTemplates = STANDARD_STATUS_TEMPLATES,
        cancelAllMmiCode = "##002#",
        verificationSourceTitle = "3GPP TS 22.030 Standard GSM Supplementary Services on Robi Axiata GSM/VoLTE",
        verificationSourceUrl = "https://www.robi.com.bd",
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = "121",
        tariffWarningEn = "Forwarded calls are billed by Robi as outgoing calls from your SIM to the destination number according to your active tariff plan.",
        tariffWarningBn = "ফরওয়ার্ড হওয়া প্রতিটি কল আপনার রবি সিম থেকে গন্তব্য নম্বরে আউটগোয়িং কল হিসেবে প্রযোজ্য ট্যারিফ অনুযায়ী চার্জ হতে পারে।",
        knownRestrictionsEn = listOf(
            "Uses standard 3GPP GSM MMI codes (**62*, **67*, **61*, **21*); support depends on SIM plan provisioning and VoLTE/IMS UT state.",
            "If an MMI code returns a connection error on VoLTE, configure forwarding via Android Phone > Settings > Call forwarding or contact Robi 121."
        ),
        knownRestrictionsBn = listOf(
            "স্ট্যান্ডার্ড 3GPP GSM MMI কোড (**62*, **67*, **61*, **21*) ব্যবহৃত হয়; সিমের প্রোভিশনিং ও VoLTE সেটিংসের ওপর নির্ভরশীল।",
            "VoLTE-তে MMI ত্রুটি দেখালে ফোনের Call forwarding সেটিংস ব্যবহার করুন অথবা ১২১ নম্বরে যোগাযোগ করুন।"
        ),
        manualInstructionsEn = "Use Phone > Settings > Calling accounts > Robi > Call forwarding, or dial **62*DestinationNumber# on your Robi SIM.",
        manualInstructionsBn = "Phone > Settings > Calling accounts > Robi > Call forwarding মেনু ব্যবহার করুন, অথবা রবি সিমে **62*গন্তব্য_নম্বর# ডায়াল করুন।"
    )

    val airtelBd = CarrierConfig(
        operatorId = OP_AIRTEL_BD,
        displayNameEn = "Airtel Bangladesh",
        displayNameBn = "এয়ারটেল বাংলাদেশ",
        mccMncCodes = setOf("470-07", "470-06"),
        originalBtrcPrefixes = setOf("016"),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.STANDARD_3GPP_PROVISIONING_DEPENDENT,
        preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
        allowMmiDialPreparation = true,
        activationMmiTemplates = STANDARD_ACTIVATION_TEMPLATES,
        deactivationMmiTemplates = STANDARD_DEACTIVATION_TEMPLATES,
        statusQueryMmiTemplates = STANDARD_STATUS_TEMPLATES,
        cancelAllMmiCode = "##002#",
        verificationSourceTitle = "3GPP TS 22.030 Standard GSM Supplementary Services (Robi Axiata / Airtel Brand)",
        verificationSourceUrl = "https://www.bd.airtel.com",
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = "121",
        tariffWarningEn = "Airtel operates on the merged Robi Axiata network; forwarded calls incur standard outgoing charges per your Airtel package.",
        tariffWarningBn = "এয়ারটেল বর্তমানে রবি আজিয়াটা নেটওয়ার্কে পরিচালিত; ফরওয়ার্ড হওয়া কলে আপনার প্যাকেজ অনুযায়ী আউটগোয়িং চার্জ প্রযোজ্য হতে পারে।",
        knownRestrictionsEn = listOf(
            "Some Airtel SIMs report MCC-MNC 470-07 while newer profiles may report 470-02 with Airtel carrier branding.",
            "Supplementary service availability depends on subscriber provisioning; contact 121 if MMI codes fail."
        ),
        knownRestrictionsBn = listOf(
            "কিছু এয়ারটেল সিমে 470-07 এবং নতুন প্রোফাইলে 470-02 নেটওয়ার্ক কোড দেখা যেতে পারে।",
            "সার্ভিস সচল না থাকলে ফোনের কল সেটিংস ব্যবহার করুন বা ১২১-এ যোগাযোগ করুন।"
        ),
        manualInstructionsEn = "Use Phone > Settings > Calling accounts > Airtel > Call forwarding, or dial **62*DestinationNumber# from your Airtel SIM.",
        manualInstructionsBn = "Phone > Settings > Calling accounts > Airtel > Call forwarding ব্যবহার করুন, অথবা এয়ারটেল সিম থেকে **62*গন্তব্য_নম্বর# ডায়াল করুন।"
    )

    val banglalink = CarrierConfig(
        operatorId = OP_BANGLALINK,
        displayNameEn = "Banglalink",
        displayNameBn = "বাংলালিংক",
        mccMncCodes = setOf("470-03"),
        originalBtrcPrefixes = setOf("019", "014"),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.STANDARD_3GPP_PROVISIONING_DEPENDENT,
        preferredMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
        allowMmiDialPreparation = true,
        activationMmiTemplates = STANDARD_ACTIVATION_TEMPLATES,
        deactivationMmiTemplates = STANDARD_DEACTIVATION_TEMPLATES,
        statusQueryMmiTemplates = STANDARD_STATUS_TEMPLATES,
        cancelAllMmiCode = "##002#",
        verificationSourceTitle = "3GPP TS 22.030 Standard GSM Supplementary Services & Banglalink Support Info",
        verificationSourceUrl = "https://banglalink.net",
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = "121",
        tariffWarningEn = "Forwarded calls are charged from your Banglalink balance according to your package's outgoing call tariff.",
        tariffWarningBn = "ফরওয়ার্ড হওয়া কল আপনার বাংলালিংক ব্যালেন্স থেকে প্যাকেজের আউটগোয়িং কলরেট অনুযায়ী চার্জ হতে পারে।",
        knownRestrictionsEn = listOf(
            "Standard 3GPP GSM MMI codes (**62*, **67*, **61*, **21*) depend on network supplementary service provisioning.",
            "When using Banglalink Wi-Fi Calling (VoWiFi) or VoLTE, ensure mobile data/IMS is stable while changing forwarding settings, or call 121 / 01911304121."
        ),
        knownRestrictionsBn = listOf(
            "স্ট্যান্ডার্ড 3GPP GSM MMI কোডের কার্যকারিতা সিম প্রোভিশনিংয়ের ওপর নির্ভরশীল।",
            "VoLTE বা Wi-Fi Calling চালু থাকলে সেটিংস পরিবর্তনের সময় সংযোগ সচল রাখুন; প্রয়োজনে ১২১ বা ০১৯১১৩০৪১২১ নম্বরে যোগাযোগ করুন।"
        ),
        manualInstructionsEn = "Use Phone > Settings > Calling accounts > Banglalink > Call forwarding, or dial **62*DestinationNumber# from your Banglalink SIM.",
        manualInstructionsBn = "Phone > Settings > Calling accounts > Banglalink > Call forwarding ব্যবহার করুন, অথবা বাংলালিংক সিমে **62*গন্তব্য_নম্বর# ডায়াল করুন।"
    )

    val teletalk = CarrierConfig(
        operatorId = OP_TELETALK,
        displayNameEn = "Teletalk",
        displayNameBn = "টেলিটক",
        mccMncCodes = setOf("470-04"),
        originalBtrcPrefixes = setOf("015"),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.OFFICIAL_HANDSET_SETTINGS_VERIFIED,
        preferredMethod = ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS,
        // Strictly adhere to Section 7: Teletalk's official VAS page documents handset Call Management settings
        // rather than publishing a short-code table, so Callora prioritizes System Call Settings rather than auto-preparing MMI!
        allowMmiDialPreparation = false,
        activationMmiTemplates = emptyMap(),
        deactivationMmiTemplates = emptyMap(),
        statusQueryMmiTemplates = emptyMap(),
        cancelAllMmiCode = null,
        verificationSourceTitle = "Teletalk Official VAS — Call Forwarding/Diverting",
        verificationSourceUrl = "https://www.teletalk.com.bd/vas/call-management-services/call-forwardingdiverting",
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = "121",
        tariffWarningEn = "Teletalk states activating the call divert feature carries no subscription fee; regular outgoing call charges apply when calls are diverted.",
        tariffWarningBn = "টেলিটকের তথ্য অনুযায়ী সার্ভিসটি চালু করতে আলাদা সাবস্ক্রিপশন ফি নেই; তবে ডাইভার্ট হওয়া কলে সাধারণ আউটগোয়িং কল চার্জ প্রযোজ্য হতে পারে।",
        knownRestrictionsEn = listOf(
            "Teletalk's official documentation instructs configuring forwarding from Handset Settings > Call Management > Call Forwarding.",
            "Because Teletalk does not publish an official MMI code table on that VAS page, Callora opens System Call Forwarding Settings instead of auto-running unverified codes."
        ),
        knownRestrictionsBn = listOf(
            "টেলিটকের অফিসিয়াল নির্দেশনায় হ্যান্ডসেটের Call Management > Call Forwarding অপশন ব্যবহারের কথা বলা হয়েছে।",
            "অফিসিয়াল কোড টেবিল প্রকাশিত না থাকায় ক্যালোরা স্বয়ংক্রিয়ভাবে কোনো আনভেরিফায়েড কোড চালায় না; সরাসরি সিস্টেম কল সেটিংস খুলে দেয়।"
        ),
        manualInstructionsEn = "Tap 'Open System Call Settings' -> select your Teletalk SIM -> Call forwarding -> When unreachable, and enter your destination number. Contact Teletalk 121 if unavailable.",
        manualInstructionsBn = "'সিস্টেম কল সেটিংস খুলুন' ট্যাপ করুন -> টেলিটক সিম নির্বাচন করুন -> Call forwarding -> When unreachable-এ গিয়ে গন্তব্য নম্বর দিন। সমস্যায় ১২১-এ যোগাযোগ করুন।"
    )

    val unknownFallback = CarrierConfig(
        operatorId = OP_UNKNOWN,
        displayNameEn = "Unknown / Other Operator",
        displayNameBn = "অজানা / অন্যান্য অপারেটর",
        mccMncCodes = emptySet(),
        originalBtrcPrefixes = emptySet(),
        supportedServices = ForwardingServiceType.entries.toSet(),
        verificationLevel = CarrierVerificationLevel.UNVERIFIED_FALLBACK,
        preferredMethod = ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS,
        allowMmiDialPreparation = false,
        activationMmiTemplates = emptyMap(),
        deactivationMmiTemplates = emptyMap(),
        statusQueryMmiTemplates = emptyMap(),
        cancelAllMmiCode = null,
        verificationSourceTitle = "Generic Safe Fallback (Unverified Operator)",
        verificationSourceUrl = null,
        lastVerifiedIsoDate = "2026-10-24",
        customerCareShortCode = null,
        tariffWarningEn = "Operator forwarding tariffs and roaming diversion charges vary widely. International roaming call forwarding can incur double international call charges.",
        tariffWarningBn = "অপারেটরভেদে কল ফরওয়ার্ডিং চার্জ ভিন্ন হতে পারে। রোমিং অবস্থায় কল ফরওয়ার্ড করলে দ্বিগুণ আন্তর্জাতিক চার্জ প্রযোজ্য হতে পারে।",
        knownRestrictionsEn = listOf(
            "Operator-specific USSD/MMI codes are not verified for this SIM.",
            "Callora will not automatically execute unverified forwarding codes. Use Android System Call Settings or consult your mobile operator."
        ),
        knownRestrictionsBn = listOf(
            "এই সিমের জন্য অপারেটর-নির্দিষ্ট কোড যাচাইকৃত নয়।",
            "ক্যালোরা কোনো আনভেরিফায়েড কোড স্বয়ংক্রিয়ভাবে চালাবে না। অ্যান্ড্রয়েডের সিস্টেম কল সেটিংস ব্যবহার করুন বা অপারেটরের সাথে যোগাযোগ করুন।"
        ),
        manualInstructionsEn = "Open Android Phone app > Settings > Calling accounts / Supplementary services > Call forwarding, or contact your mobile carrier's customer support.",
        manualInstructionsBn = "ফোনের Phone অ্যাপ > Settings > Calling accounts / Supplementary services > Call forwarding ব্যবহার করুন অথবা আপনার অপারেটরের কাস্টমার কেয়ারে যোগাযোগ করুন।"
    )

    val allBangladeshOperators: List<CarrierConfig> = listOf(
        grameenphone,
        robi,
        airtelBd,
        banglalink,
        teletalk
    )

    data class CarrierResolution(
        val config: CarrierConfig,
        val matchedByMccMnc: Boolean,
        val matchedByDisplayNameOnly: Boolean
    )

    fun resolveForSubscription(sim: SimSubscription): CarrierResolution {
        val code = sim.mccMnc
        val nameTokens = "${sim.carrierName} ${sim.displayName}".lowercase()

        if (code == "470-01") {
            return CarrierResolution(grameenphone, matchedByMccMnc = true, matchedByDisplayNameOnly = false)
        }
        if (code == "470-07" || code == "470-06") {
            return CarrierResolution(airtelBd, matchedByMccMnc = true, matchedByDisplayNameOnly = false)
        }
        if (code == "470-02") {
            // Merged Airtel-Robi network: distinguish Airtel-branded SIMs on 470-02
            val chosen = if ("airtel" in nameTokens) airtelBd else robi
            return CarrierResolution(chosen, matchedByMccMnc = true, matchedByDisplayNameOnly = false)
        }
        if (code == "470-03") {
            return CarrierResolution(banglalink, matchedByMccMnc = true, matchedByDisplayNameOnly = false)
        }
        if (code == "470-04") {
            return CarrierResolution(teletalk, matchedByMccMnc = true, matchedByDisplayNameOnly = false)
        }

        // Only consider display-name fallback if MCC is empty or 470 (Bangladesh),
        // and explicitly flag matchedByDisplayNameOnly = true.
        if (sim.mcc.isBlank() || sim.mcc == "470") {
            val byName = when {
                "grameenphone" in nameTokens || "skitto" in nameTokens || nameTokens.trim() == "gp" -> grameenphone
                "airtel" in nameTokens -> airtelBd
                "robi" in nameTokens -> robi
                "banglalink" in nameTokens -> banglalink
                "teletalk" in nameTokens -> teletalk
                else -> null
            }
            if (byName != null) {
                return CarrierResolution(byName, matchedByMccMnc = false, matchedByDisplayNameOnly = true)
            }
        }

        return CarrierResolution(unknownFallback, matchedByMccMnc = false, matchedByDisplayNameOnly = false)
    }

    fun getByOperatorId(operatorId: String): CarrierConfig =
        allBangladeshOperators.firstOrNull { it.operatorId == operatorId } ?: unknownFallback
}
