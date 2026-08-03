package com.example

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashlyticsHelper {
    private const val TAG = "CrashlyticsHelper"

    fun init(context: Context) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // 1. Device type
            val deviceType = when {
                RemoteAdsManager.isAndroidTv(context) -> "Android TV"
                RemoteAdsManager.isTablet(context) -> "Tablet"
                else -> "Phone"
            }
            crashlytics.setCustomKey("device_type", deviceType)

            // 2. Android version
            crashlytics.setCustomKey("android_version", Build.VERSION.RELEASE)
            crashlytics.setCustomKey("sdk_int", Build.VERSION.SDK_INT)

            // 3. Device model
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            crashlytics.setCustomKey("device_model", deviceModel)

            // 4. App version
            var appVersion = "unknown"
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appVersion = packageInfo.versionName ?: "unknown"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting app version name", e)
            }
            crashlytics.setCustomKey("app_version", appVersion)

            Log.d(TAG, "Crashlytics initialized with keys: device_type=$deviceType, android_version=${Build.VERSION.RELEASE}, device_model=$deviceModel, app_version=$appVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize custom keys for Crashlytics", e)
        }
    }

    fun recordNonFatal(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record non-fatal exception to Crashlytics", e)
        }
    }
}
