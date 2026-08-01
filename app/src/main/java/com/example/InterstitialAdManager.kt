package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"
    private val ADMOB_INTERSTITIAL_ID: String
        get() = RemoteAdsManager.getInterstitialId()
    private const val INTERSTITIAL_FREQUENCY_CAP_MS = 150_000L

    private var mInterstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShowTime: Long = 0

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
                adType = "interstitial",
                adUnitId = ADMOB_INTERSTITIAL_ID,
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

    fun loadInterstitial(context: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                loadInterstitial(context)
            }
            return
        }

        // Prevent load if SDK is not initialized
        if (!MediationAdManager.isSdkInitialized()) {
            Log.d(TAG, "Preventing loadInterstitial: AdMob SDK is not fully initialized. Current status: ${MediationAdManager.sdkInitStatus}")
            setStatus(AdLoadingStatus.NOT_INITIALIZED, context, "SDK Not Initialized Load Prevented")
            return
        }

        // If ads are disabled via remote config, do not load
        if (!RemoteAdsManager.isInterstitialEnabled(context)) {
            Log.d(TAG, "Interstitial Ad is disabled via Remote Config. Skipping load.")
            return
        }

        // Prevent duplicate load requests while an ad is already loading or showing.
        if (currentStatus == AdLoadingStatus.LOADING || currentStatus == AdLoadingStatus.SHOWING) {
            Log.d(TAG, "Preventing loadInterstitial: Ad is currently in state $currentStatus")
            return
        }

        if (mInterstitialAd != null) {
            setStatus(AdLoadingStatus.LOADED, context, "Ad Available Check")
            return
        }

        adRequestStartTime = System.currentTimeMillis()
        adLoadedTime = 0
        adFailedTime = 0
        adShownTime = 0
        adDismissedTime = 0

        setStatus(AdLoadingStatus.LOADING, context, "Ad Load Request Started")
        isLoadingInterstitial = true
        Log.d(TAG, "Requesting AdMob Interstitial load.")

        AdAnalyticsHelper.logAdDiagnostic(
            context = context,
            eventName = "Ad request started",
            adType = "interstitial",
            adUnitId = ADMOB_INTERSTITIAL_ID,
            retryAttempt = retryAttempt,
            sdkInitStartTime = MediationAdManager.sdkInitStartTime,
            sdkInitEndTime = MediationAdManager.sdkInitEndTime,
            adRequestStartTime = adRequestStartTime
        )

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            ADMOB_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    adLoadedTime = System.currentTimeMillis()
                    mInterstitialAd = interstitialAd
                    isLoadingInterstitial = false
                    Log.d(TAG, "AdMob Interstitial loaded successfully.")
                    
                    val prevRetry = retryAttempt
                    retryAttempt = 0 // reset retry attempt on successful load

                    setStatus(AdLoadingStatus.LOADED, context, "Ad Loaded Successfully")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Ad loaded successfully",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
                        responseInfo = interstitialAd.responseInfo,
                        retryAttempt = prevRetry,
                        sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                        sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                        adRequestStartTime = adRequestStartTime,
                        adLoadedTime = adLoadedTime
                    )
                    
                    AdAnalyticsHelper.logAdEvent(
                        context = context,
                        eventName = "interstitial_loaded",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    adFailedTime = System.currentTimeMillis()
                    mInterstitialAd = null
                    isLoadingInterstitial = false
                    Log.e(TAG, "AdMob Interstitial failed to load: ${loadAdError.message}")
                    
                    setStatus(AdLoadingStatus.FAILED, context, "Ad Failed To Load")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Ad failed to load",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
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
                        eventName = "interstitial_failed",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )

                    // Exponential backoff retry mechanism
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        retryAttempt++
                        val delayMs = INITIAL_RETRY_DELAY_MS * Math.pow(2.0, (retryAttempt - 1).toDouble()).toLong()
                        Log.d(TAG, "Scheduling Interstitial Ad load retry attempt $retryAttempt of $MAX_RETRY_ATTEMPTS in $delayMs ms.")
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(delayMs)
                            if (currentStatus != AdLoadingStatus.LOADING && currentStatus != AdLoadingStatus.LOADED && currentStatus != AdLoadingStatus.SHOWING) {
                                loadInterstitial(context)
                            }
                        }
                    } else {
                        Log.e(TAG, "Reached maximum Interstitial Ad load retry attempts ($MAX_RETRY_ATTEMPTS). Stopping retries.")
                    }
                }
            }
        )
    }

    fun showInterstitialIfReady(activity: Activity) {
        // Prevent showing ads if activity is finishing or destroyed
        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity is finishing or destroyed. Skipping Interstitial ad.")
            return
        }

        // Check if enabled via Remote Config
        if (!RemoteAdsManager.isInterstitialEnabled(activity)) {
            Log.d(TAG, "Interstitial Ad is disabled via Remote Config. Skipping show.")
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShowTime < INTERSTITIAL_FREQUENCY_CAP_MS) {
            val secondsRemaining = (INTERSTITIAL_FREQUENCY_CAP_MS - (currentTime - lastInterstitialShowTime)) / 1000
            Log.d(TAG, "Interstitial suppressed by frequency cap. Wait $secondsRemaining seconds.")
            return
        }

        val ad = mInterstitialAd
        if (ad != null) {
            val hasFocus = activity.hasWindowFocus()
            Log.d(TAG, "Showing AdMob Interstitial. Activity window focus state: $hasFocus")
            
            adShownTime = System.currentTimeMillis()
            setStatus(AdLoadingStatus.SHOWING, activity, "Ad Showing")

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    adDismissedTime = System.currentTimeMillis()
                    setStatus(AdLoadingStatus.DISMISSED, activity, "Ad Dismissed")

                    AdAnalyticsHelper.logAdDiagnostic(
                        context = activity,
                        eventName = "Ad dismissed",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
                        sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                        sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                        adRequestStartTime = adRequestStartTime,
                        adLoadedTime = adLoadedTime,
                        adShownTime = adShownTime,
                        adDismissedTime = adDismissedTime
                    )
                    
                    AdAnalyticsHelper.logAdEvent(
                        context = activity,
                        eventName = "interstitial_closed",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID
                    )
                    mInterstitialAd = null
                    // Preload next Interstitial immediately after dismissal
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    val failToShowTime = System.currentTimeMillis()
                    setStatus(AdLoadingStatus.FAILED, activity, "Ad Failed To Show")

                    AdAnalyticsHelper.logAdDiagnostic(
                        context = activity,
                        eventName = "Ad failed to show",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
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
                        eventName = "interstitial_failed",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
                        errorCode = adError.code,
                        errorMessage = adError.message
                    )
                    mInterstitialAd = null
                    // Preload next Interstitial after failure
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    lastInterstitialShowTime = System.currentTimeMillis()
                    Log.d(TAG, "AdMob Interstitial ad shown successfully.")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = activity,
                        eventName = "Ad shown",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID,
                        responseInfo = ad.responseInfo,
                        sdkInitStartTime = MediationAdManager.sdkInitStartTime,
                        sdkInitEndTime = MediationAdManager.sdkInitEndTime,
                        adRequestStartTime = adRequestStartTime,
                        adLoadedTime = adLoadedTime,
                        adShownTime = adShownTime
                    )
                    
                    AdAnalyticsHelper.logAdEvent(
                        context = activity,
                        eventName = "interstitial_shown",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID
                    )
                }

                override fun onAdClicked() {
                    AdAnalyticsHelper.logAdEvent(
                        context = activity,
                        eventName = "interstitial_clicked",
                        adType = "interstitial",
                        adUnitId = ADMOB_INTERSTITIAL_ID
                    )
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "AdMob Interstitial not ready yet. Pre-loading for next time.")
            loadInterstitial(activity.applicationContext)
        }
    }
}
