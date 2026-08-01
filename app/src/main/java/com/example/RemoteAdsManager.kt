package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object RemoteAdsManager {
    private const val TAG = "RemoteAdsManager"
    private const val PREFS_NAME = "remote_ads_prefs"
    private const val KEY_CONFIG_JSON = "config_json"
    
    private const val REMOTE_URL = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/ads.json"

    // Locally cached variables for fast lookup, initialized to default (enabled)
    @Volatile
    private var adsEnabled = true

    @Volatile
    private var adMobAppId = "ca-app-pub-4402921387999545~7695131497"

    @Volatile
    private var adMobBannerId = "ca-app-pub-4402921387999545/8164635863"

    @Volatile
    private var adMobInterstitialId = "ca-app-pub-4402921387999545/5552449815"

    @Volatile
    private var adMobAppOpenId = "ca-app-pub-4402921387999545/5065218868"
    
    @Volatile
    private var phoneEnabled = true
    @Volatile
    private var phoneBanner = true
    @Volatile
    private var phoneInterstitial = true
    @Volatile
    private var phoneAppOpen = true

    @Volatile
    private var tabletEnabled = true
    @Volatile
    private var tabletBanner = true
    @Volatile
    private var tabletInterstitial = true
    @Volatile
    private var tabletAppOpen = true

    @Volatile
    private var tvEnabled = true
    @Volatile
    private var tvBanner = true
    @Volatile
    private var tvInterstitial = true
    @Volatile
    private var tvAppOpen = true

    fun init(context: Context) {
        Log.d(TAG, "Initializing RemoteAdsManager...")
        // Offload all file read/write (SharedPreferences & Asset Loading) and network operations to background thread
        CoroutineScope(Dispatchers.IO).launch {
            loadFromCache(context)
            fetchRemoteConfig(context)
        }
    }

    private fun loadFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (jsonStr != null) {
            Log.d(TAG, "Loaded cached configuration: $jsonStr")
            parseAndApply(jsonStr)
        } else {
            Log.d(TAG, "No cached configuration found. Trying to load from assets...")
            val assetsJson = loadJsonFromAssets(context, "ads_config.json")
            if (assetsJson != null && parseAndApply(assetsJson)) {
                Log.d(TAG, "Successfully loaded configuration from assets.")
                saveToCache(context, assetsJson)
            } else {
                Log.d(TAG, "Failed to load from assets. Using default values.")
                applyDefaults()
            }
        }
    }

    private fun loadJsonFromAssets(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading $fileName from assets: ${e.message}")
            CrashlyticsHelper.recordNonFatal(e)
            null
        }
    }

    private fun saveToCache(context: Context, jsonStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG_JSON, jsonStr).apply()
        Log.d(TAG, "Saved configuration to cache.")
    }

    private fun applyDefaults() {
        adsEnabled = true
        adMobAppId = "ca-app-pub-4402921387999545~7695131497"
        adMobBannerId = "ca-app-pub-4402921387999545/8164635863"
        adMobInterstitialId = "ca-app-pub-4402921387999545/5552449815"
        adMobAppOpenId = "ca-app-pub-4402921387999545/5065218868"
        phoneEnabled = true
        phoneBanner = true
        phoneInterstitial = true
        phoneAppOpen = true

        tabletEnabled = true
        tabletBanner = true
        tabletInterstitial = true
        tabletAppOpen = true

        tvEnabled = true
        tvBanner = true
        tvInterstitial = true
        tvAppOpen = true
    }

    private fun parseAndApply(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val ads = root.getJSONObject("ads")
            
            adsEnabled = ads.optBoolean("enabled", adsEnabled)
            
            // Parse dynamic AdMob IDs (checking both under root and under ads object)
            adMobAppId = ads.optString("app_id", root.optString("app_id", adMobAppId))
            adMobBannerId = ads.optString("banner_id", root.optString("banner_id", adMobBannerId))
            adMobInterstitialId = ads.optString("interstitial_id", root.optString("interstitial_id", adMobInterstitialId))
            adMobAppOpenId = ads.optString("app_open_id", root.optString("app_open_id", adMobAppOpenId))

            // Parse phone config
            val phone = ads.optJSONObject("phone")
            if (phone != null) {
                phoneEnabled = phone.optBoolean("enabled", phoneEnabled)
                phoneBanner = phone.optBoolean("banner", phoneBanner)
                phoneInterstitial = phone.optBoolean("interstitial", phoneInterstitial)
                phoneAppOpen = phone.optBoolean("app_open", phoneAppOpen)
            }

            // Parse tablet config
            val tablet = ads.optJSONObject("tablet")
            if (tablet != null) {
                tabletEnabled = tablet.optBoolean("enabled", tabletEnabled)
                tabletBanner = tablet.optBoolean("banner", tabletBanner)
                tabletInterstitial = tablet.optBoolean("interstitial", tabletInterstitial)
                tabletAppOpen = tablet.optBoolean("app_open", tabletAppOpen)
            }

            // Parse tv config
            val tv = ads.optJSONObject("tv")
            if (tv != null) {
                tvEnabled = tv.optBoolean("enabled", tvEnabled)
                tvBanner = tv.optBoolean("banner", tvBanner)
                tvInterstitial = tv.optBoolean("interstitial", tvInterstitial)
                tvAppOpen = tv.optBoolean("app_open", tvAppOpen)
            }

            Log.d(TAG, "Remote Ads JSON Parse Success")
            Log.d(TAG, "Ads Configuration Applied")
            Log.d(TAG, "Successfully parsed configuration: adsEnabled=$adsEnabled, " +
                    "App ID=$adMobAppId, Interstitial ID=$adMobInterstitialId, App Open ID=$adMobAppOpenId, " +
                    "Phone(enabled=$phoneEnabled, banner=$phoneBanner, interstitial=$phoneInterstitial, app_open=$phoneAppOpen), " +
                    "Tablet(enabled=$tabletEnabled, banner=$tabletBanner, interstitial=$tabletInterstitial, app_open=$tabletAppOpen), " +
                    "TV(enabled=$tvEnabled, banner=$tvBanner, interstitial=$tvInterstitial, app_open=$tvAppOpen)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ads configuration JSON: ${e.message}", e)
            CrashlyticsHelper.recordNonFatal(e)
            false
        }
    }

    private suspend fun fetchRemoteConfig(context: Context) {
        Log.d(TAG, "Remote Ads JSON Download Started")
        Log.d(TAG, "Fetching remote ads configuration from $REMOTE_URL")
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(REMOTE_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected HTTP response code: ${response.code}")
                }
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrEmpty()) {
                    Log.d(TAG, "Remote Ads JSON Download Success")
                    Log.d(TAG, "Successfully fetched remote ads configuration: $bodyString")
                    if (parseAndApply(bodyString)) {
                        saveToCache(context, bodyString)
                        // Trigger loading of ads since configuration is loaded now!
                        withContext(Dispatchers.Main) {
                            MediationAdManager.loadAppOpenAd(context)
                            MediationAdManager.loadInterstitial(context)
                        }
                    }
                } else {
                    throw IOException("Empty response body received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote ads configuration: ${e.message}. Using last cached or default values.", e)
            CrashlyticsHelper.recordNonFatal(e)
            // Fallback is automatic since cache was already loaded at startup
        }
    }

    // --- Device Detection Utility Functions ---

    fun isAndroidTv(context: Context): Boolean {
        val pm = context.packageManager
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTvMode = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        return isTvMode || 
               pm.hasSystemFeature("android.software.leanback") || 
               pm.hasSystemFeature("amazon.hardware.fire_tv") ||
               pm.hasSystemFeature("android.hardware.type.television") ||
               pm.hasSystemFeature("com.google.android.tv")
    }

    fun getDeviceType(context: Context): String {
        if (isAndroidTv(context)) return "Android TV"
        
        val smallestWidthDp = if (context is android.app.Activity) {
            try {
                val metrics = androidx.window.layout.WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(context)
                val bounds = metrics.bounds
                val density = context.resources.displayMetrics.density
                val widthDp = bounds.width() / density
                val heightDp = bounds.height() / density
                minOf(widthDp, heightDp)
            } catch (e: Exception) {
                val metrics = context.resources.displayMetrics
                val widthDp = metrics.widthPixels / metrics.density
                val heightDp = metrics.heightPixels / metrics.density
                minOf(widthDp, heightDp)
            }
        } else {
            val metrics = context.resources.displayMetrics
            val widthDp = metrics.widthPixels / metrics.density
            val heightDp = metrics.heightPixels / metrics.density
            minOf(widthDp, heightDp)
        }
        
        val screenLayout = context.resources.configuration.screenLayout
        val isLargeScreen = (screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
        
        return if (smallestWidthDp >= 600 || isLargeScreen) {
            "Tablet"
        } else {
            "Phone"
        }
    }

    fun isTablet(context: Context): Boolean {
        return getDeviceType(context) == "Tablet"
    }

    fun isPhone(context: Context): Boolean {
        return getDeviceType(context) == "Phone"
    }

    // --- Public Ad Config Accessors ---

    fun isAdsEnabled(): Boolean {
        return adsEnabled
    }

    fun getAppId(): String {
        return adMobAppId
    }

    fun getBannerId(): String {
        return adMobBannerId
    }

    fun getInterstitialId(): String {
        return adMobInterstitialId
    }

    fun getAppOpenId(): String {
        return adMobAppOpenId
    }

    fun isAppOpenEnabled(context: Context): Boolean {
        if (!adsEnabled) return false
        return when {
            isAndroidTv(context) -> tvEnabled && tvAppOpen
            isTablet(context) -> tabletEnabled && tabletAppOpen
            else -> phoneEnabled && phoneAppOpen
        }
    }

    fun isInterstitialEnabled(context: Context): Boolean {
        if (!adsEnabled) return false
        return when {
            isAndroidTv(context) -> tvEnabled && tvInterstitial
            isTablet(context) -> tabletEnabled && tabletInterstitial
            else -> phoneEnabled && phoneInterstitial
        }
    }

    fun isBannerEnabled(context: Context): Boolean {
        if (!adsEnabled) return false
        return when {
            isAndroidTv(context) -> tvEnabled && tvBanner
            isTablet(context) -> tabletEnabled && tabletBanner
            else -> phoneEnabled && phoneBanner
        }
    }
}
