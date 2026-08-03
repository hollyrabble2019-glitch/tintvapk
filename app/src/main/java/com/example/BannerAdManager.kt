package com.example

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class BannerAdState {
    object Disabled : BannerAdState()
    object Idle : BannerAdState()
    object Loading : BannerAdState()
    object Loaded : BannerAdState()
    data class Error(val error: LoadAdError) : BannerAdState()
}

/**
 * Helper class that handles AdMob banner initialization and loading states,
 * ensuring that banner ads are properly fetched and injected into UI components
 * only when ads are enabled via remote configuration.
 */
object BannerAdManager {
    private const val TAG = "BannerAdManager"

    private val _bannerState = MutableStateFlow<BannerAdState>(BannerAdState.Idle)
    val bannerState: StateFlow<BannerAdState> = _bannerState.asStateFlow()

    /**
     * Checks whether banner ads are enabled according to the remote configuration.
     */
    fun isBannerEnabled(context: Context): Boolean {
        return RemoteAdsManager.isBannerEnabled(context)
    }

    /**
     * Fetches the banner ad unit ID configured via remote configuration.
     */
    fun getBannerAdUnitId(): String {
        return RemoteAdsManager.getBannerId()
    }

    /**
     * Calculates the adaptive banner ad size based on context screen width.
     */
    fun calculateAdSize(context: Context): AdSize {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val adWidthPixels = displayMetrics.widthPixels
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    /**
     * Encapsulates creation and initialization of an AdView instance for AdMob banner ads.
     * Banner ads are fetched and injected ONLY when enabled via the remote JSON configuration.
     *
     * @param context Application/Activity context
     * @param onStateChanged Callback notifying when the banner loading state updates
     * @return Initialized AdView instance ready to render, or null if banner ads are disabled.
     */
    fun createAndLoadBannerView(
        context: Context,
        onStateChanged: ((BannerAdState) -> Unit)? = null
    ): AdView? {
        if (!isBannerEnabled(context)) {
            Log.d(TAG, "Banner ads disabled in remote configuration. Skipping AdView creation.")
            _bannerState.value = BannerAdState.Disabled
            onStateChanged?.invoke(BannerAdState.Disabled)
            return null
        }

        val adUnitId = getBannerAdUnitId()
        if (adUnitId.isBlank()) {
            Log.w(TAG, "Banner AdUnit ID is blank. Cannot load banner ad.")
            _bannerState.value = BannerAdState.Disabled
            onStateChanged?.invoke(BannerAdState.Disabled)
            return null
        }

        Log.d(TAG, "Initializing and loading AdView with unit ID: $adUnitId")
        _bannerState.value = BannerAdState.Loading
        onStateChanged?.invoke(BannerAdState.Loading)

        val adView = AdView(context).apply {
            setAdSize(calculateAdSize(context))
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad successfully loaded.")
                    _bannerState.value = BannerAdState.Loaded
                    onStateChanged?.invoke(BannerAdState.Loaded)
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Banner ad loaded",
                        adType = "banner",
                        adUnitId = adUnitId,
                        sdkInitStatus = MediationAdManager.sdkInitStatus.name
                    )
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Banner ad failed to load: ${error.message} (code: ${error.code})")
                    _bannerState.value = BannerAdState.Error(error)
                    onStateChanged?.invoke(BannerAdState.Error(error))
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Banner ad failed to load",
                        adType = "banner",
                        adUnitId = adUnitId,
                        sdkInitStatus = MediationAdManager.sdkInitStatus.name
                    )
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Banner ad clicked.")
                    AdAnalyticsHelper.logAdDiagnostic(
                        context = context,
                        eventName = "Banner ad clicked",
                        adType = "banner",
                        adUnitId = adUnitId,
                        sdkInitStatus = MediationAdManager.sdkInitStatus.name
                    )
                }
            }
            loadAd(AdRequest.Builder().build())
        }

        return adView
    }
}
