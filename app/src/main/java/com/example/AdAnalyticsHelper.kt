package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.android.gms.ads.ResponseInfo

object AdAnalyticsHelper {
    private const val TAG = "AdAnalyticsHelper"

    private fun getDeviceType(context: Context): String {
        return when {
            RemoteAdsManager.isAndroidTv(context) -> "tv"
            RemoteAdsManager.isTablet(context) -> "tablet"
            else -> "phone"
        }
    }

    /**
     * Logs an AdMob ad lifecycle event to Firebase Analytics with standard parameters.
     */
    fun logAdEvent(
        context: Context,
        eventName: String,
        adType: String,
        adUnitId: String,
        errorCode: Int? = null,
        errorMessage: String? = null
    ) {
        try {
            val deviceType = getDeviceType(context)
            val bundle = Bundle().apply {
                putString("device_type", deviceType)
                putString("ad_type", adType)
                putString("ad_unit_id", adUnitId)
                errorCode?.let { putInt("error_code", it) }
                errorMessage?.let { putString("error_message", it) }
            }
            FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
            Log.d(TAG, "Logged Ad Event: $eventName with params: device_type=$deviceType, ad_type=$adType, ad_unit_id=$adUnitId, error_code=$errorCode, error_message=$errorMessage")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging ad event to Firebase Analytics: ${e.message}", e)
        }
    }

    /**
     * Logs detailed diagnostic information of an AdMob event to Logcat and reports to Firebase Crashlytics.
     */
    fun logAdDiagnostic(
        context: Context,
        eventName: String, // e.g. "Ad request started", "Ad loaded successfully", "Ad failed to load", "Ad shown", "Ad dismissed"
        adType: String,    // "app_open", "interstitial", or "sdk_init"
        adUnitId: String,
        errorCode: Int? = null,
        errorMessage: String? = null,
        responseInfo: ResponseInfo? = null,
        sdkInitStatus: String? = null,
        retryAttempt: Int? = null,
        stateTransition: String? = null,
        // Performance latency fields
        sdkInitStartTime: Long? = null,
        sdkInitEndTime: Long? = null,
        adRequestStartTime: Long? = null,
        adLoadedTime: Long? = null,
        adFailedTime: Long? = null,
        adShownTime: Long? = null,
        adDismissedTime: Long? = null
    ) {
        val calculatedDeviceType = RemoteAdsManager.getDeviceType(context)
        val adapterClass = responseInfo?.mediationAdapterClassName ?: "N/A"
        val resolvedSdkInitStatus = sdkInitStatus ?: MediationAdManager.sdkInitStatus.name

        val networkType = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                when {
                    capabilities == null -> "No Connection"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Other"
                }
            } else "N/A"
        } catch (e: Exception) {
            "Unknown"
        }

        val currentTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())

        val logMsg = buildString {
            append("========================= AdDiagnostics =========================\n")
            append("EVENT: [$eventName]\n")
            append("Timestamp: $currentTimestamp\n")
            append("Ad Type: $adType\n")
            append("Ad Unit ID: $adUnitId\n")
            append("Device Type: $calculatedDeviceType\n")
            append("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            append("Network Type: $networkType\n")
            append("SDK Init Status: $resolvedSdkInitStatus\n")

            if (sdkInitStartTime != null) {
                append("SDK Init Start: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(sdkInitStartTime))}\n")
            }
            if (sdkInitEndTime != null) {
                append("SDK Init Completion: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(sdkInitEndTime))}\n")
            }
            if (sdkInitStartTime != null && sdkInitEndTime != null) {
                append("SDK Init Duration: ${sdkInitEndTime - sdkInitStartTime} ms\n")
            }

            if (adRequestStartTime != null) {
                append("Ad Request Start: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(adRequestStartTime))}\n")
            }
            if (adLoadedTime != null) {
                append("Ad Loaded Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(adLoadedTime))}\n")
                if (adRequestStartTime != null) {
                    append("Total Load Duration: ${adLoadedTime - adRequestStartTime} ms\n")
                }
            }
            if (adFailedTime != null) {
                append("Ad Failed Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(adFailedTime))}\n")
                if (adRequestStartTime != null) {
                    append("Total Load Attempt Duration: ${adFailedTime - adRequestStartTime} ms\n")
                }
            }
            if (adShownTime != null) {
                append("Ad Shown Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(adShownTime))}\n")
            }
            if (adDismissedTime != null) {
                append("Ad Dismissed Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(adDismissedTime))}\n")
            }

            if (retryAttempt != null) append("Retry Attempt Number: $retryAttempt\n")
            if (stateTransition != null) append("Ad State Transition: $stateTransition\n")
            if (errorCode != null) append("Error Code: $errorCode\n")
            if (errorMessage != null) append("Error Message: $errorMessage\n")
            append("Mediation Adapter Class: $adapterClass\n")
            append("Response Info: ${responseInfo?.toString() ?: "N/A"}\n")
            append("=================================================================")
        }

        if (errorCode != null || errorMessage != null) {
            Log.e("AdDiagnostics", logMsg)
        } else {
            Log.d("AdDiagnostics", logMsg)
        }

        // 1. Log to Crashlytics custom logs
        try {
            FirebaseCrashlytics.getInstance().log(logMsg)
        } catch (e: Exception) {
            Log.e("AdDiagnostics", "Error sending diagnostic to Crashlytics logs", e)
        }

        // 2. Record as non-fatal exception
        try {
            val exceptionMessage = buildString {
                append("Ad Diagnostic ($adType): $eventName")
                if (errorCode != null) append(" (Error: $errorCode - $errorMessage)")
                if (sdkInitStartTime != null && sdkInitEndTime != null) append(" [Init: ${sdkInitEndTime - sdkInitStartTime}ms]")
                if (adRequestStartTime != null && adLoadedTime != null) append(" [Load: ${adLoadedTime - adRequestStartTime}ms]")
            }
            FirebaseCrashlytics.getInstance().recordException(Exception(exceptionMessage))
        } catch (e: Exception) {
            Log.e("AdDiagnostics", "Error recording diagnostic non-fatal exception", e)
        }
    }
}
