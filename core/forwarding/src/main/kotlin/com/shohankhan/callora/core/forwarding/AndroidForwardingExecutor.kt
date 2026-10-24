package com.shohankhan.callora.core.forwarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.shohankhan.callora.core.common.CalloraClock
import com.shohankhan.callora.core.common.GsmMmiCodec
import com.shohankhan.callora.core.model.ForwardingExecutionMethod
import com.shohankhan.callora.core.model.ForwardingOperationResult
import com.shohankhan.callora.core.model.ForwardingRequest
import com.shohankhan.callora.core.model.ForwardingResultCode
import com.shohankhan.callora.core.model.ForwardingVerificationState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Production Android implementation of [ForwardingExecutor].
 *
 * Truthfulness & Platform Safety:
 * - Never calls hidden/privileged @SystemApi CallForwardingInfo methods.
 * - Uses Uri.fromParts("tel", mmi, null) for ACTION_DIAL so '#' is properly encoded (%23).
 * - Never claims ACTION_DIAL verified network-side forwarding.
 * - Handles TelephonyManager.sendUssdRequest errors including Android 17 QPR2's
 *   USSD_ERROR_NOT_ALLOWED restriction gracefully by providing immediate dialer fallback.
 */
class AndroidForwardingExecutor(
    private val context: Context,
    private val clock: CalloraClock = CalloraClock.SYSTEM
) : ForwardingExecutor {

    override suspend fun execute(
        request: ForwardingRequest,
        preparedMmiCode: String?,
        activeSimCount: Int
    ): ForwardingOperationResult {
        val now = clock.nowMillis()
        return when (request.preferredMethod) {
            ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS -> {
                val opened = openSystemCallSettings()
                if (opened) {
                    ForwardingOperationResult.AcceptedUnverified(
                        preparedMmiCode = null,
                        executionMethod = ForwardingExecutionMethod.SYSTEM_CALL_SETTINGS,
                        simSlotSelectionGuaranteed = false,
                        noteCode = "OPENED_SYSTEM_CALL_SETTINGS",
                        timestampMillis = now
                    )
                } else {
                    ForwardingOperationResult.Failure(
                        resultCode = ForwardingResultCode.UNSUPPORTED,
                        verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                        preparedMmiCode = preparedMmiCode,
                        diagnosticCode = "NO_CALL_SETTINGS_ACTIVITY_FOUND",
                        suggestSystemCallSettings = false,
                        timestampMillis = now
                    )
                }
            }

            ForwardingExecutionMethod.SAFE_DIALER_INTENT -> {
                if (preparedMmiCode.isNullOrBlank()) {
                    return ForwardingOperationResult.Failure(
                        resultCode = ForwardingResultCode.FAILED,
                        verificationState = ForwardingVerificationState.FAILED_OPERATION,
                        preparedMmiCode = null,
                        diagnosticCode = "EMPTY_MMI_CODE",
                        timestampMillis = now
                    )
                }
                launchSafeDialerIntent(request, preparedMmiCode, activeSimCount, now)
            }

            ForwardingExecutionMethod.DIRECT_USSD_API -> {
                if (preparedMmiCode.isNullOrBlank()) {
                    return ForwardingOperationResult.Failure(
                        resultCode = ForwardingResultCode.FAILED,
                        verificationState = ForwardingVerificationState.FAILED_OPERATION,
                        preparedMmiCode = null,
                        diagnosticCode = "EMPTY_MMI_CODE",
                        timestampMillis = now
                    )
                }
                executeDirectUssd(request, preparedMmiCode)
            }
        }
    }

    private fun launchSafeDialerIntent(
        request: ForwardingRequest,
        mmiCode: String,
        activeSimCount: Int,
        now: Long
    ): ForwardingOperationResult {
        val uri = Uri.fromParts("tel", mmiCode, null)
        val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Best-effort PhoneAccountHandle hint for dual-SIM dialers when permitted.
        // Note: Android OEM dialers are not guaranteed to lock SIM selection on ACTION_DIAL,
        // so simSlotSelectionGuaranteed is ONLY true when activeSimCount == 1.
        resolvePhoneAccountHandleForSubId(request.subscriptionId)?.let { handle ->
            dialIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        return try {
            context.startActivity(dialIntent)
            ForwardingOperationResult.AcceptedUnverified(
                preparedMmiCode = mmiCode,
                executionMethod = ForwardingExecutionMethod.SAFE_DIALER_INTENT,
                simSlotSelectionGuaranteed = activeSimCount == 1,
                noteCode = "OPENED_IN_SYSTEM_DIALER_UNVERIFIED",
                timestampMillis = now
            )
        } catch (_: Exception) {
            ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.UNSUPPORTED,
                verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                preparedMmiCode = mmiCode,
                diagnosticCode = "NO_DIALER_ACTIVITY_AVAILABLE",
                fallbackMmiCode = mmiCode,
                suggestSystemCallSettings = true,
                timestampMillis = now
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun executeDirectUssd(
        request: ForwardingRequest,
        mmiCode: String
    ): ForwardingOperationResult {
        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPhone) {
            return ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.PERMISSION_REQUIRED,
                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                preparedMmiCode = mmiCode,
                diagnosticCode = "CALL_PHONE_PERMISSION_REQUIRED",
                fallbackMmiCode = mmiCode,
                timestampMillis = clock.nowMillis()
            )
        }

        val baseTelephony = context.getSystemService(TelephonyManager::class.java)
            ?: return ForwardingOperationResult.Failure(
                resultCode = ForwardingResultCode.UNSUPPORTED,
                verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                preparedMmiCode = mmiCode,
                diagnosticCode = "TELEPHONY_SERVICE_UNAVAILABLE",
                fallbackMmiCode = mmiCode,
                timestampMillis = clock.nowMillis()
            )

        val scopedTelephony = baseTelephony.createForSubscriptionId(request.subscriptionId)
        val mainHandler = Handler(Looper.getMainLooper())

        val timedResult = withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine<ForwardingOperationResult> { cont ->
                val callback = object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        ussdRequest: String?,
                        response: CharSequence?
                    ) {
                        if (!cont.isActive) return
                        val now = clock.nowMillis()
                        val parsed = GsmMmiCodec.parseCarrierResponse(response?.toString())
                        val mapped = when (parsed) {
                            is GsmMmiCodec.ParsedCarrierResponse.AuthoritativeStatus ->
                                ForwardingOperationResult.VerifiedSuccess(
                                    preparedMmiCode = mmiCode,
                                    carrierRawSummary = parsed.sanitizedSummary,
                                    observedActive = parsed.isEnabled,
                                    observedDestinationMasked = parsed.extractedDestinationMasked,
                                    timestampMillis = now
                                )

                            is GsmMmiCodec.ParsedCarrierResponse.AmbiguousAcknowledgement ->
                                ForwardingOperationResult.AcceptedUnverified(
                                    preparedMmiCode = mmiCode,
                                    executionMethod = ForwardingExecutionMethod.DIRECT_USSD_API,
                                    simSlotSelectionGuaranteed = true,
                                    noteCode = "CARRIER_ACK_WITHOUT_AUTHORITATIVE_STATE",
                                    timestampMillis = now
                                )

                            is GsmMmiCodec.ParsedCarrierResponse.CarrierError ->
                                ForwardingOperationResult.Failure(
                                    resultCode = ForwardingResultCode.FAILED,
                                    verificationState = ForwardingVerificationState.FAILED_OPERATION,
                                    preparedMmiCode = mmiCode,
                                    diagnosticCode = parsed.reasonCode,
                                    fallbackMmiCode = mmiCode,
                                    timestampMillis = now
                                )
                        }
                        cont.resume(mapped)
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        ussdRequest: String?,
                        failureCode: Int
                    ) {
                        if (!cont.isActive) return
                        val now = clock.nowMillis()
                        // Note: failureCode -1 = SEND_USSD_FAILED, -2 = USSD_ERROR_SERVICE_UNAVAIL,
                        // and Android 17 QPR2 introduces USSD_ERROR_NOT_ALLOWED for call-forwarding codes.
                        val isDisallowedOrUnsupported =
                            failureCode != TelephonyManager.USSD_RETURN_FAILURE &&
                                failureCode != TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL
                        val result = ForwardingOperationResult.Failure(
                            resultCode = if (isDisallowedOrUnsupported) {
                                ForwardingResultCode.UNSUPPORTED
                            } else {
                                ForwardingResultCode.FAILED
                            },
                            verificationState = if (isDisallowedOrUnsupported) {
                                ForwardingVerificationState.UNSUPPORTED_CAPABILITY
                            } else {
                                ForwardingVerificationState.FAILED_OPERATION
                            },
                            preparedMmiCode = mmiCode,
                            diagnosticCode = when (failureCode) {
                                TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD_SERVICE_UNAVAILABLE"
                                TelephonyManager.USSD_RETURN_FAILURE -> "USSD_MMI_REJECTED_TRY_DIALER"
                                else -> "USSD_NOT_ALLOWED_BY_OS_USE_DIALER_$failureCode"
                            },
                            fallbackMmiCode = mmiCode,
                            suggestSystemCallSettings = true,
                            timestampMillis = now
                        )
                        cont.resume(result)
                    }
                }

                try {
                    scopedTelephony.sendUssdRequest(mmiCode, callback, mainHandler)
                } catch (_: SecurityException) {
                    if (cont.isActive) {
                        cont.resume(
                            ForwardingOperationResult.Failure(
                                resultCode = ForwardingResultCode.PERMISSION_REQUIRED,
                                verificationState = ForwardingVerificationState.FAILED_OPERATION,
                                preparedMmiCode = mmiCode,
                                diagnosticCode = "SECURITY_EXCEPTION_ON_SEND_USSD",
                                fallbackMmiCode = mmiCode,
                                timestampMillis = clock.nowMillis()
                            )
                        )
                    }
                } catch (_: Exception) {
                    if (cont.isActive) {
                        cont.resume(
                            ForwardingOperationResult.Failure(
                                resultCode = ForwardingResultCode.UNSUPPORTED,
                                verificationState = ForwardingVerificationState.UNSUPPORTED_CAPABILITY,
                                preparedMmiCode = mmiCode,
                                diagnosticCode = "DIRECT_USSD_UNSUPPORTED_ON_DEVICE",
                                fallbackMmiCode = mmiCode,
                                timestampMillis = clock.nowMillis()
                            )
                        )
                    }
                }
            }
        }

        return timedResult ?: ForwardingOperationResult.Failure(
            resultCode = ForwardingResultCode.FAILED,
            verificationState = ForwardingVerificationState.FAILED_OPERATION,
            preparedMmiCode = mmiCode,
            diagnosticCode = "USSD_RESPONSE_TIMEOUT",
            fallbackMmiCode = mmiCode,
            timestampMillis = clock.nowMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneAccountHandleForSubId(subscriptionId: Int): PhoneAccountHandle? {
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPhoneState) return null

        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
            val subIdStr = subscriptionId.toString()
            telecom.callCapablePhoneAccounts.firstOrNull { handle ->
                handle.id == subIdStr || handle.id.endsWith(subIdStr)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun copyMmiToClipboard(label: String, mmiCode: String): Boolean = try {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, mmiCode))
        clipboard != null
    } catch (_: Exception) {
        false
    }

    override fun openSystemCallSettings(): Boolean {
        val candidates = listOf(
            Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }
}
