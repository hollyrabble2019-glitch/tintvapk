package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppOpenAdManager {
    private const val TAG = "AppOpenAdManager"
    private val ADMOB_APP_OPEN_ID: String
        get() = RemoteAdsManager.getAppOpenId()

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpenAd = false
    private var appOpenAdLoadTime: Long = 0
    private var appOpenAdShownOnStart = false

    private var adRequestStartTime: Long = 0
    private var adLoadedTime: Long = 0
    private var adFailedTime: Long = 0
    private var adShownTime: Long = 0
    private var adDismissedTime: Long = 0

    @Volatile
    private var currentStatus: AdLoadingStatus = AdLoadingStatus.NOT_INITIALIZED

    private var retryAttempt = 0
    private const val MAX_RETRY_ATTEMPTS = 5
    private const val INITIAL_RETRY_DELAY_MS = 2000L // 2 seconds

    private fun setStatus(newStatus: AdLoadingStatus, context: Context, eventName: String? = null) {
        val oldStatus = currentStatus
        if (oldStatus != newStatus) {
            currentStatus = newStatus
            val transitionStr = "$oldStatus -> $newStatus"
            Log.d(TAG, "AdLoadingStatus State transition: $transitionStr")
            AdAnalyticsHelper.logAdDiagnostic(
                context = context,
                eventName = eventName ?: "State transition",
                adType = "app_open",
                adUnitId = ADMOB_APP_OPEN_ID,
                sdkInitStatus = MediationAdManager.sdkInitStatus.name,
                retryAttempt = retryAttempt,
                stateTransition = transitionStr,
                sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                adRequestStartTime = if (adRequestStartTime > 0) adRequestStartTime else null,
                adLoadedTime = if (adLoadedTime > 0) adLoadedTime else null,
                adFailedTime = if (adFailedTime > 0) adFailedTime else null,
                adShownTime = if (adShownTime > 0) adShownTime else null,
                adDismissedTime = if (adDismissedTime > 0) adDismissedTime else null
            )
        }
    }

    fun loadAppOpenAd(context: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                loadAppOpenAd(context)
            }
            return
        }

        // Prevent load if SDK is not initialized
        if (!MediationAdManager.isSdkInitialized()) {
            Log.d(TAG, "Preventing loadAppOpenAd: AdMob SDK is not fully initialized. Current status: ${MediationAdManager.sdkInitStatus}")
            setStatus(AdLoadingStatus.NOT_INITIALIZED, context, "SDK Not Initialized Load Prevented")
            return
        }

        // If ads are disabled via remote config, do not load
        if (!RemoteAdsManager.isAppOpenEnabled(context)) {
            Log.d(TAG, "App Open Ad is disabled via Remote Config. Skipping load.")
            return
        }

        // Prevent duplicate load requests while an ad is already loading or showing.
        if (currentStatus == AdLoadingStatus.LOADING || currentStatus == AdLoadingStatus.SHOWING) {
            Log.d(TAG, "Preventing loadAppOpenAd: Ad is currently in state $currentStatus")
            return
        }

        if (isAppOpenAdAvailable()) {
            setStatus(AdLoadingStatus.LOADED, context, "Ad Available Check")
            return
        }

        adRequestStartTime = System.currentTimeMillis()
        adLoadedTime = 0
        adFailedTime = 0
        adShownTime = 0
        adDismissedTime = 0

        setStatus(AdLoadingStatus.LOADING, context, "Ad Load Request Started")
        isLoadingAppOpenAd = true
        Log.d(TAG, "App Open Ad Loading")
        Log.d(TAG, "Loading App Open Ad...")

        AdAnalyticsHelper.logAdDiagnostic(
            context = context,
            eventName = "Ad request started",
            adType = "app_open",
            adUnitId = ADMOB_APP_OPEN_ID,
            retryAttempt = retryAttempt,
            sdkInitStartTime = MediationAdManager.sdkInitStartTime,
            sdkInitEndTime = MediationAdManager.sdkInitEndTime,
            adRequestStartTime = adRequestStartTime
        )

        val request = AdRequest.Builder().build()

        // Determine orientation dynamically for tablets and Android TV devices to prevent load failures.
        val orientation = if (RemoteAdsManager.isAndroidTv(context) || 
                              context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            AppOpenAd.APP_OPEN_AD_ORIENTATION_LANDSCAPE
        } else {
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT
        }

        AppOpenAd.load(
            context,
            ADMOB_APP_OPEN_ID,
            request,
            orientation,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    adLoadedTime = System.currentTimeMillis()
                    appOpenAd = ad
                    isLoadingAppOpenAd = false
                    appOpenAdLoadTime = System.currentTimeMillis()
                    Log.d(TAG, "App Open Ad Loaded successfully.")
                    
                    val prevRetry = retryAttempt
                    retryAttempt = 0 // reset retry attempt on successful load

                    setStatus(AdLoadingStatus.LOADED, context, "Ad Loaded Successfully")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Ad loaded successfully",
                        adType = "app_open",
                        adUnitId = ADMOB_APP_OPEN_ID,
                        responseInfo = ad.responseInfo,
                        retryAttempt = prevRetry,
                        sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                        sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                        adRequestStartTime = adRequestStartTime,
                        adLoadedTime = adLoadedTime
                    )
                    
                    AdAnalyticsHelper.logAdEvent(
                        context = context,
                        eventName = "app_open_loaded",
                        adType = "app_open",
                        adUnitId = ADMOB_APP_OPEN_ID
                    )
                    
                    // Show on cold-start once loaded, if the app is still in foreground and not playing
                    if (MediationAdManager.isAppInForeground && !MediationAdManager.isPlayerScreenActive) {
                        MediationAdManager.currentActivity?.let { activity ->
                            showAppOpenAdIfAvailable(activity)
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    adFailedTime = System.currentTimeMillis()
                    isLoadingAppOpenAd = false
                    Log.e(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    
                    setStatus(AdLoadingStatus.FAILED, context, "Ad Failed To Load")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Ad failed to load",
                        adType = "app_open",
                        adUnitId = ADMOB_APP_OPEN_ID,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message,
                        responseInfo = loadAdError.responseInfo,
                        retryAttempt = retryAttempt,
                        sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                        sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                        adRequestStartTime = adRequestStartTime,
                        adFailedTime = adFailedTime
                    )
                    
                    AdAnalyticsHelper.logAdEvent(
                        context = context,
                        eventName = "app_open_failed",
                        adType = "app_open",
                        adUnitId = ADMOB_APP_OPEN_ID,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                    
                    // Exponential backoff retry mechanism
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        retryAttempt++
                        val delayMs = INITIAL_RETRY_DELAY_MS * Math.pow(2.0, (retryAttempt - 1).toDouble()).toLong()
                        Log.d(TAG, "Scheduling App Open Ad load retry attempt $retryAttempt of $MAX_RETRY_ATTEMPTS in $delayMs ms.")
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(delayMs)
                            if (currentStatus != AdLoadingStatus.LOADING && currentStatus != AdLoadingStatus.LOADED && currentStatus != AdLoadingStatus.SHOWING) {
                                loadAppOpenAd(context)
                            }
                        }
                    } else {
                        Log.e(TAG, "Reached maximum App Open Ad load retry attempts ($MAX_RETRY_ATTEMPTS). Stopping retries.")
                    }
                }
            }
        )
    }

    fun isAppOpenAdAvailable(): Boolean {
        val age = System.currentTimeMillis() - appOpenAdLoadTime
        return appOpenAd != null && age < 4 * 3600_000L
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: () -> Unit = {}) {
        // Prevent showing ads if activity is finishing or destroyed
        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity is finishing or destroyed. Skipping App Open ad.")
            onComplete()
            return
        }

        // Check if enabled via Remote Config
        if (!RemoteAdsManager.isAppOpenEnabled(activity)) {
            Log.d(TAG, "App Open Ad is disabled via Remote Config. Skipping show.")
            onComplete()
            return
        }

        if (MediationAdManager.isPlayerScreenActive) {
            Log.d(TAG, "App Open Ad suppressed: live stream player is currently active.")
            onComplete()
            return
        }

        if (appOpenAdShownOnStart) {
            Log.d(TAG, "App Open Ad already shown on start. Skipping subsequent shows.")
            onComplete()
            return
        }

        if (!isAppOpenAdAvailable()) {
            Log.d(TAG, "App Open Ad not available to show. Loading a new one.")
            loadAppOpenAd(activity)
            onComplete()
            return
        }

        val hasFocus = activity.hasWindowFocus()
        Log.d(TAG, "Preparing to show App Open Ad. Focus acquired: $hasFocus")

        adShownTime = System.currentTimeMillis()
        setStatus(AdLoadingStatus.SHOWING, activity, "Ad Showing")

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adDismissedTime = System.currentTimeMillis()
                setStatus(AdLoadingStatus.DISMISSED, activity, "Ad Dismissed")

                AdAnalyticsHelper.logAdDiagnostic(
                    context = activity,
                    eventName = "Ad dismissed",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID,
                    sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                    sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                    adRequestStartTime = adRequestStartTime,
                    adLoadedTime = adLoadedTime,
                    adShownTime = adShownTime,
                    adDismissedTime = adDismissedTime
                )
                
                AdAnalyticsHelper.logAdEvent(
                    context = activity,
                    eventName = "app_open_closed",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID
                )
                appOpenAd = null
                onComplete()
                // Preload next App Open Ad after dismissal
                loadAppOpenAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                val failToShowTime = System.currentTimeMillis()
                setStatus(AdLoadingStatus.FAILED, activity, "Ad Failed To Show")

                AdAnalyticsHelper.logAdDiagnostic(
                    context = activity,
                    eventName = "Ad failed to show",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID,
                    errorCode = adError.code,
                    errorMessage = adError.message,
                    sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                    sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                    adRequestStartTime = adRequestStartTime,
                    adLoadedTime = adLoadedTime,
                    adFailedTime = failToShowTime
                )
                
                AdAnalyticsHelper.logAdEvent(
                    context = activity,
                    eventName = "app_open_failed",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID,
                    errorCode = adError.code,
                    errorMessage = adError.message
                )
                appOpenAd = null
                onComplete()
                // Preload next App Open Ad after failure
                loadAppOpenAd(activity.applicationContext)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad Showing")
                Log.d(TAG, "App Open Ad shown to user.")
                
                AdAnalyticsHelper.logAdDiagnostic(
                    context = activity,
                    eventName = "Ad shown",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID,
                    responseInfo = appOpenAd?.responseInfo,
                    sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                    sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                    adRequestStartTime = adRequestStartTime,
                    adLoadedTime = adLoadedTime,
                    adShownTime = adShownTime
                )
                
                AdAnalyticsHelper.logAdEvent(
                    context = activity,
                    eventName = "app_open_shown",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID
                )
                appOpenAdShownOnStart = true
            }

            override fun onAdClicked() {
                AdAnalyticsHelper.logAdEvent(
                    context = activity,
                    eventName = "app_open_clicked",
                    adType = "app_open",
                    adUnitId = ADMOB_APP_OPEN_ID
                )
            }
        }
        appOpenAd?.show(activity)
    }

    fun resetShowOnStartFlag() {
        Log.d(TAG, "Resetting App Open Ad show on start flag.")
        appOpenAdShownOnStart = false
    }
}
