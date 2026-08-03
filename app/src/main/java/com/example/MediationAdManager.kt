package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object MediationAdManager : Application.ActivityLifecycleCallbacks {

    private const val TAG = "MediationAdManager"

    var currentActivity: Activity? = null
    
    // App background tracking
    private var startedActivityCount = 0
    var isAppInForeground = false
    
    // Flag to suppress ads during active player session
    var isPlayerScreenActive = false

    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)

    @Volatile
    var sdkInitStatus: AdLoadingStatus = AdLoadingStatus.NOT_INITIALIZED
        private set

    var sdkInitStartTime: Long = 0
        private set
    var sdkInitEndTime: Long = 0
        private set

    fun init(application: Application) {
        // Register Activity Lifecycle Callbacks
        application.registerActivityLifecycleCallbacks(this)
        
        // Initialize Remote Ads Configuration System
        RemoteAdsManager.init(application)
        
        // Initialize Google Mobile Ads SDK asynchronously on a background thread
        initializeMobileAdsSdk(application)
    }

    private fun initializeMobileAdsSdk(context: Context) {
        if (isInitialized.get() || !isInitializing.compareAndSet(false, true)) {
            Log.d(TAG, "SDK Initialization already in progress or completed.")
            return
        }

        sdkInitStartTime = System.currentTimeMillis()
        sdkInitStatus = AdLoadingStatus.INITIALIZING
        Log.d(TAG, "AdMob SDK Initialization started. Status: INITIALIZING")
        AdAnalyticsHelper.logAdDiagnostic(
            context = context,
            eventName = "SDK initialization started",
            adType = "sdk_init",
            adUnitId = "N/A",
            sdkInitStatus = "INITIALIZING",
            sdkInitStartTime = sdkInitStartTime
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Calling MobileAds.initialize() on IO background thread...")
                MobileAds.initialize(context) { status ->
                    sdkInitEndTime = System.currentTimeMillis()
                    isInitialized.set(true)
                    isInitializing.set(false)
                    sdkInitStatus = AdLoadingStatus.LOADED
                    Log.d(TAG, "AdMob SDK Initialization completed successfully. Status: LOADED")
                    
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "SDK initialization completed successfully",
                        adType = "sdk_init",
                        adUnitId = "N/A",
                        sdkInitStatus = "LOADED",
                        sdkInitStartTime = sdkInitStartTime,
                        sdkInitEndTime = sdkInitEndTime
                    )

                    // Load pre-requisite ads on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        loadAppOpenAd(context)
                        loadInterstitial(context)
                    }
                }
            } catch (e: Exception) {
                isInitializing.set(false)
                sdkInitStatus = AdLoadingStatus.FAILED
                Log.e(TAG, "AdMob SDK Initialization failed: ${e.message}", e)
                val endTime = System.currentTimeMillis()
                
                AdAnalyticsHelper.logAdDiagnostic(
                    context = context,
                    eventName = "SDK initialization failed",
                    adType = "sdk_init",
                    adUnitId = "N/A",
                    errorCode = -1,
                    errorMessage = e.message ?: "Unknown error",
                    sdkInitStatus = "FAILED",
                    sdkInitStartTime = sdkInitStartTime,
                    sdkInitEndTime = endTime
                )
            }
        }
    }

    fun isSdkInitialized(): Boolean {
        return sdkInitStatus == AdLoadingStatus.LOADED
    }

    // --- Google AdMob App Open Ads ---

    fun loadAppOpenAd(context: Context) {
        AppOpenAdManager.loadAppOpenAd(context)
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: () -> Unit = {}) {
        AppOpenAdManager.showAppOpenAdIfAvailable(activity, onComplete)
    }

    // --- Google AdMob Interstitial Ads ---

    fun loadInterstitial(context: Context) {
        InterstitialAdManager.loadInterstitial(context)
    }

    fun showInterstitialIfReady(activity: Activity) {
        InterstitialAdManager.showInterstitialIfReady(activity)
    }

    // --- ActivityLifecycleCallbacks Implementation ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        startedActivityCount++
        
        if (startedActivityCount == 1 && !isAppInForeground) {
            isAppInForeground = true
            Log.d(TAG, "App returned to foreground. Preparing to show App Open Ad.")
            // Reset the show-on-start flag so that the ad can be shown on resume
            AppOpenAdManager.resetShowOnStartFlag()
            showAppOpenAdIfAvailable(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount == 0) {
            isAppInForeground = false
            Log.d(TAG, "App entered background.")
            AppOpenAdManager.resetShowOnStartFlag()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }
}
