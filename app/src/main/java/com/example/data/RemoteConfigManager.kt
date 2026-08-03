package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Remote JSON Data Models ---

data class PoweredByConfig(
    val text: String = "Powered by Ouskare",
    val color: String = "#FFFFFF",
    val size: Float = 14f
)

data class AppConfig(
    val name: String = "Tinghir TV",
    val logo: String = "",
    val splashLogo: String = "",
    val splashBackground: String = "",
    val maintenance: Boolean = false,
    val welcomeMessage: String = "Welcome to Tinghir TV",
    val maintenanceTitle: String = "تنبيه الصيانة | Maintenance",
    val maintenanceMessage: String = "التطبيق قيد الصيانة حالياً. يرجى المحاولة لاحقاً.",
    val websiteUrl: String = "https://tinghirtv.site",
    val privacyPolicy: String = "",
    val termsUrl: String = "",
    val version: String = "1.0.0",
    val status: String = "active",
    val poweredBy: PoweredByConfig = PoweredByConfig()
)

data class CategoryConfig(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val icon: String = "",
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val order: Int = 0,
    val badge: String = "",
    val badgeColor: String = "",
    val backgroundColor: String = "",
    val textColor: String = ""
)

data class ServerConfig(
    val name: String = "Server 1",
    val type: String = "m3u8",
    val url: String = "",
    val priority: Int = 1,
    val enabled: Boolean = true
)

data class LiveMatchConfigItem(
    val id: String = "",
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val order: Int = 0,
    val competition: String = "",
    val competitionLogo: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val matchDate: String = "",
    val matchTime: String = "",
    val status: String = "UPCOMING",
    val minute: String = "",
    val thumbnail: String = "",
    val language: String = "Arabic",
    val servers: List<ServerConfig> = emptyList()
)

data class LiveMatchesConfig(
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val title: String = "Live",
    val logo: String = "",
    val order: Int = 1,
    val autoRefresh: Boolean = true,
    val refreshInterval: Int = 60,
    val matches: List<LiveMatchConfigItem> = emptyList()
)

data class MenuItemConfig(
    val title: String = "",
    val icon: String = "",
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val order: Int = 0,
    val action: String = "",
    val url: String = ""
)

data class PopupMessage(
    val enabled: Boolean = false,
    val title: String = "",
    val message: String = "",
    val buttonText: String = "OK",
    val showOnce: Boolean = false,
    val yes: String = "Yes",
    val no: String = "No"
)

data class MessagesConfig(
    val welcome: PopupMessage = PopupMessage(enabled = true, title = "Welcome", message = "Welcome to Powered by Ouskare", buttonText = "OK", showOnce = true),
    val announcement: PopupMessage = PopupMessage(),
    val maintenance: PopupMessage = PopupMessage(title = "Maintenance", message = "The application is currently under maintenance.", buttonText = "OK"),
    val serverOffline: PopupMessage = PopupMessage(enabled = true, title = "Server Offline", message = "This channel is temporarily unavailable.", buttonText = "OK"),
    val playbackError: PopupMessage = PopupMessage(enabled = true, title = "Playback Error", message = "Failed to play stream.", buttonText = "OK"),
    val internet: PopupMessage = PopupMessage(enabled = true, title = "No Internet", message = "Please check your internet connection.", buttonText = "Retry"),
    val update: PopupMessage = PopupMessage(enabled = true, title = "Update Available", message = "A new version of Powered by Ouskare is available.", buttonText = "Update"),
    val forceUpdate: PopupMessage = PopupMessage(enabled = false, title = "Update Required", message = "You must update the application to continue.", buttonText = "Update"),
    val exitDialog: PopupMessage = PopupMessage(enabled = true, title = "Exit", message = "Are you sure you want to exit?", yes = "Yes", no = "No"),
    val customPopup: PopupMessage = PopupMessage(),
    val toastChannelChanged: String = "Channel Changed",
    val toastCategoryChanged: String = "Category Changed",
    val toastAddedFavorite: String = "Added to Favorites",
    val toastRemovedFavorite: String = "Removed from Favorites"
)

data class SettingsConfig(
    val autoPlay: Boolean = true,
    val retryCount: Int = 3,
    val bufferTimeout: Int = 10,
    val keepScreenOn: Boolean = true,
    val rememberLastChannel: Boolean = true,
    val remoteNavigation: Boolean = true,
    val nextPreviousChannel: Boolean = true,
    val gridColumns: Int = 4,
    val showLogos: Boolean = true,
    val showNames: Boolean = true,
    val favoritesEnabled: Boolean = true,
    val searchEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val aboutEnabled: Boolean = true,
    val shareEnabled: Boolean = true,
    val appVersion: String = "1.0.0",
    val website: String = "https://tinghirtv.site",
    val facebook: String = "https://facebook.com/yourpage",
    val telegram: String = "https://t.me/yourchannel",
    val youtube: String = "",
    val instagram: String = "",
    val tiktok: String = "",
    val whatsapp: String = "",
    val contactEmail: String = "contact@tinghirtv.site",
    val supportEmail: String = "support@tinghirtv.site",
    val privacyPolicyUrl: String = ""
)

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val PREFS_NAME = "remote_config_store"

    // Remote URLs
    const val URL_CONFIG = "https://raw.githubusercontent.com/sakifooo/channels_data/main/config.json"
    const val URL_CATEGORIES = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/categories.json"
    const val URL_CHANNELS = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/channels.json"
    const val URL_LIVE_MATCHES = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/live_matches.json"
    const val URL_ADS = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/ads.json"
    const val URL_UPDATE = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/update.json"
    const val URL_MENU = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/menu.json"
    const val URL_MESSAGES = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/messages.json"
    const val URL_SETTINGS = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/settings.json"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // StateFlows
    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryConfig>>(
        parseCategories(DEFAULT_CATEGORIES_JSON) ?: emptyList()
    )
    val categories: StateFlow<List<CategoryConfig>> = _categories.asStateFlow()

    private val _liveMatches = MutableStateFlow(LiveMatchesConfig())
    val liveMatches: StateFlow<LiveMatchesConfig> = _liveMatches.asStateFlow()

    private val _menu = MutableStateFlow<List<MenuItemConfig>>(
        parseMenu(DEFAULT_MENU_JSON) ?: emptyList()
    )
    val menu: StateFlow<List<MenuItemConfig>> = _menu.asStateFlow()

    private val _messages = MutableStateFlow(MessagesConfig())
    val messages: StateFlow<MessagesConfig> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(SettingsConfig())
    val settings: StateFlow<SettingsConfig> = _settings.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        Log.d(TAG, "Initializing RemoteConfigManager...")

        CoroutineScope(Dispatchers.IO).launch {
            // Load cache first
            loadAllFromCache(context)
            // Fetch all remote configs asynchronously
            fetchAllRemoteConfigs(context)
        }
    }

    private fun loadAllFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Config
        val configStr = prefs.getString("cache_config", null)
        if (configStr != null) {
            Log.d(TAG, "[config.json] Cache Loaded")
            parseAppConfig(configStr)?.let { _appConfig.value = it }
        }

        // 2. Categories
        val categoriesStr = prefs.getString("cache_categories", null)
        if (categoriesStr != null) {
            Log.d(TAG, "[categories.json] Cache Loaded")
            parseCategories(categoriesStr)?.let { _categories.value = it }
        }

        // 3. Live Matches
        val matchesStr = prefs.getString("cache_live_matches", null)
        if (matchesStr != null) {
            Log.d(TAG, "[live_matches.json] Cache Loaded")
            parseLiveMatches(matchesStr)?.let { _liveMatches.value = it }
        }

        // 4. Menu
        val menuStr = prefs.getString("cache_menu", null)
        if (menuStr != null) {
            Log.d(TAG, "[menu.json] Cache Loaded")
            parseMenu(menuStr)?.let { _menu.value = it }
        }

        // 5. Messages
        val messagesStr = prefs.getString("cache_messages", null)
        if (messagesStr != null) {
            Log.d(TAG, "[messages.json] Cache Loaded")
            parseMessages(messagesStr)?.let { _messages.value = it }
        }

        // 6. Settings
        val settingsStr = prefs.getString("cache_settings", null)
        if (settingsStr != null) {
            Log.d(TAG, "[settings.json] Cache Loaded")
            parseSettings(settingsStr)?.let { _settings.value = it }
        }
    }

    suspend fun fetchAllRemoteConfigs(context: Context) = withContext(Dispatchers.IO) {
        launch { fetchConfig(context) }
        launch { fetchCategories(context) }
        launch { fetchLiveMatches(context) }
        launch { fetchMenu(context) }
        launch { fetchMessages(context) }
        launch { fetchSettings(context) }
    }

    private suspend fun fetchRemoteJson(name: String, url: String): String? {
        Log.d(TAG, "[$name] JSON Download Started: $url")
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP response ${response.code} for $name")
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) throw IOException("Empty body for $name")
                Log.d(TAG, "[$name] JSON Download Success")
                body
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Configuration Failed: ${e.message}", e)
            null
        }
    }

    private fun saveToCache(context: Context, key: String, content: String, name: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(key, content).apply()
            Log.d(TAG, "[$name] Cache Saved")
        } catch (e: Exception) {
            Log.e(TAG, "[$name] Failed saving to cache: ${e.message}")
        }
    }

    // --- Parsers ---

    private suspend fun fetchConfig(context: Context) {
        val json = fetchRemoteJson("config.json", URL_CONFIG) ?: return
        val parsed = parseAppConfig(json)
        if (parsed != null) {
            Log.d(TAG, "[config.json] JSON Parse Success")
            saveToCache(context, "cache_config", json, "config.json")
            _appConfig.value = parsed
            Log.d(TAG, "[config.json] Configuration Applied")
        }
    }

    private fun parseAppConfig(json: String): AppConfig? {
        return try {
            val root = JSONObject(json)
            val appObj = if (root.has("app")) root.getJSONObject("app") else root

            val poweredByObj = if (appObj.has("powered_by")) appObj.optJSONObject("powered_by") else null
            val poweredBy = PoweredByConfig(
                text = poweredByObj?.optString("text", "Powered by Ouskare") ?: appObj.optString("powered_by_text", "Powered by Ouskare"),
                color = poweredByObj?.optString("color", "#FFFFFF") ?: appObj.optString("powered_by_color", "#FFFFFF"),
                size = (poweredByObj?.optDouble("size", 14.0) ?: appObj.optDouble("powered_by_size", 14.0)).toFloat()
            )

            val rawLogo = appObj.optString("logo", "")
            val rawSplashLogo = appObj.optString("splash_logo", rawLogo)

            AppConfig(
                name = appObj.optString("name", "Tinghir TV"),
                logo = rawLogo,
                splashLogo = rawSplashLogo,
                splashBackground = appObj.optString("splash_background", ""),
                maintenance = appObj.optBoolean("maintenance", false),
                welcomeMessage = appObj.optString("welcome_message", "Welcome to Tinghir TV"),
                maintenanceTitle = appObj.optString("maintenance_title", "تنبيه الصيانة | Maintenance"),
                maintenanceMessage = appObj.optString("maintenance_message", appObj.optString("welcome_message", "التطبيق قيد الصيانة حالياً. يرجى المحاولة لاحقاً.")),
                websiteUrl = appObj.optString("website_url", "https://tinghirtv.site"),
                privacyPolicy = appObj.optString("privacy_policy", ""),
                termsUrl = appObj.optString("terms_url", ""),
                version = appObj.optString("version", "1.0.0"),
                status = appObj.optString("status", "active"),
                poweredBy = poweredBy
            )
        } catch (e: Exception) {
            Log.e(TAG, "[config.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchCategories(context: Context) {
        val json = fetchRemoteJson("categories.json", URL_CATEGORIES) ?: return
        val parsed = parseCategories(json)
        if (parsed != null) {
            Log.d(TAG, "[categories.json] JSON Parse Success")
            saveToCache(context, "cache_categories", json, "categories.json")
            _categories.value = parsed
            Log.d(TAG, "[categories.json] Configuration Applied")
        }
    }

    fun parseCategories(json: String): List<CategoryConfig>? {
        return try {
            val list = mutableListOf<CategoryConfig>()
            val trimmed = json.trim()
            val arr = if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("categories")) root.getJSONArray("categories")
                else if (root.has("data")) root.getJSONArray("data")
                else if (root.has("items")) root.getJSONArray("items")
                else JSONArray()
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONArray()
            }
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val logoStr = obj.optString("logo", obj.optString("flag", obj.optString("icon", "")))
                val iconStr = obj.optString("icon", logoStr)
                list.add(
                    CategoryConfig(
                        id = obj.optString("id", "cat_$i"),
                        name = obj.optString("name", obj.optString("title", "")),
                        logo = logoStr,
                        icon = iconStr,
                        enabled = obj.optBoolean("enabled", true),
                        hidden = obj.optBoolean("hidden", false),
                        order = obj.optInt("order", i + 1),
                        badge = obj.optString("badge", ""),
                        badgeColor = obj.optString("badge_color", ""),
                        backgroundColor = obj.optString("background_color", ""),
                        textColor = obj.optString("text_color", "")
                    )
                )
            }
            list.sortedBy { it.order }
        } catch (e: Exception) {
            Log.e(TAG, "[categories.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }

    private const val DEFAULT_MENU_JSON = """
    {
      "menu": [
        {
          "title": "Facebook",
          "enabled": true,
          "icon": "https://cdn.simpleicons.org/facebook/1877F2",
          "url": "https://facebook.com/yourpage"
        },
        {
          "title": "Telegram",
          "enabled": true,
          "icon": "https://cdn.simpleicons.org/telegram/26A5E4",
          "url": "https://t.me/yourchannel"
        },
        {
          "title": "Contact Us",
          "enabled": true,
          "icon": "https://cdn.simpleicons.org/gmail/EA4335",
          "url": "mailto:contact@tinghirtv.site"
        },
        {
          "title": "Website",
          "enabled": true,
          "icon": "https://cdn.simpleicons.org/googlechrome/4285F4",
          "url": "https://tinghirtv.site"
        }
      ]
    }
    """

    private const val DEFAULT_CATEGORIES_JSON = """
    {
      "categories": [
        {
          "id": "bein_sports",
          "name": "beIN SPORTS",
          "logo": "http://kinglogo.newtvhd.com/logoo/BEIN4K/HD/bein%20sports%20news%20hd.png",
          "enabled": true,
          "order": 1
        },
        {
          "id": "alwan_sport",
          "name": "ALWAN SPORT",
          "logo": "http://kinglogo.newtvhd.com/logoo/AL%20MAJD/ALWAN.png",
          "enabled": true,
          "order": 2
        },
        {
          "id": "thmanyah",
          "name": "THMANYAH",
          "logo": "https://www.wzufa.com/wp-content/uploads/2025/07/Thmanyah.png",
          "enabled": true,
          "order": 3
        },
        {
          "id": "morocco",
          "name": "Morocco",
          "logo": "https://flagcdn.com/w320/ma.png",
          "enabled": true,
          "order": 4
        },
        {
          "id": "algeria",
          "name": "Algeria",
          "logo": "https://flagcdn.com/w320/dz.png",
          "enabled": true,
          "order": 5
        },
        {
          "id": "tunisia",
          "name": "Tunisia",
          "logo": "https://flagcdn.com/w320/tn.png",
          "enabled": true,
          "order": 6
        },
        {
          "id": "libya",
          "name": "Libya",
          "logo": "https://flagcdn.com/w320/ly.png",
          "enabled": true,
          "order": 7
        },
        {
          "id": "egypt",
          "name": "Egypt",
          "logo": "https://flagcdn.com/w320/eg.png",
          "enabled": true,
          "order": 8
        },
        {
          "id": "saudi_arabia",
          "name": "Saudi Arabia",
          "logo": "https://flagcdn.com/w320/sa.png",
          "enabled": true,
          "order": 9
        },
        {
          "id": "united_arab_emirates",
          "name": "United Arab Emirates",
          "logo": "https://flagcdn.com/w320/ae.png",
          "enabled": true,
          "order": 10
        },
        {
          "id": "qatar",
          "name": "Qatar",
          "logo": "https://flagcdn.com/w320/qa.png",
          "enabled": true,
          "order": 11
        }
      ]
    }
    """

    private suspend fun fetchLiveMatches(context: Context) {
        val json = fetchRemoteJson("live_matches.json", URL_LIVE_MATCHES) ?: return
        val parsed = parseLiveMatches(json)
        if (parsed != null) {
            Log.d(TAG, "[live_matches.json] JSON Parse Success")
            saveToCache(context, "cache_live_matches", json, "live_matches.json")
            _liveMatches.value = parsed
            Log.d(TAG, "[live_matches.json] Configuration Applied")
        }
    }

    fun parseLiveMatches(json: String): LiveMatchesConfig? {
        return try {
            val root = JSONObject(json)
            val enabled = root.optBoolean("enabled", true)
            val hidden = root.optBoolean("hidden", false)
            val title = root.optString("title", "Live")
            val logo = root.optString("logo", "")
            val order = root.optInt("order", 1)
            val autoRefresh = root.optBoolean("auto_refresh", true)
            val refreshInterval = root.optInt("refresh_interval", 60)

            val matchesList = mutableListOf<LiveMatchConfigItem>()
            val matchesArr = if (root.has("matches")) root.getJSONArray("matches") else JSONArray()
            for (i in 0 until matchesArr.length()) {
                val mObj = matchesArr.getJSONObject(i)
                val serversList = mutableListOf<ServerConfig>()
                val sArr = when {
                    mObj.has("servers") -> mObj.getJSONArray("servers")
                    mObj.has("streams") -> mObj.getJSONArray("streams")
                    mObj.has("sources") -> mObj.getJSONArray("sources")
                    mObj.has("links") -> mObj.getJSONArray("links")
                    else -> null
                }
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(j)
                        val sName = sObj.optString("name", sObj.optString("title", "Server ${j + 1}"))
                        val sType = sObj.optString("type", sObj.optString("player", sObj.optString("format", "m3u8")))
                        val sUrl = sObj.optString("url", sObj.optString("link", sObj.optString("src", "")))
                        val sPriority = sObj.optInt("priority", j + 1)
                        val sEnabled = sObj.optBoolean("enabled", true)
                        if (sUrl.isNotBlank()) {
                            serversList.add(
                                ServerConfig(
                                    name = sName,
                                    type = sType,
                                    url = sUrl,
                                    priority = sPriority,
                                    enabled = sEnabled
                                )
                            )
                        }
                    }
                }
                if (serversList.isEmpty()) {
                    val fallbackUrl = mObj.optString("stream_url", mObj.optString("url", mObj.optString("link", "")))
                    if (fallbackUrl.isNotBlank()) {
                        val fallbackType = mObj.optString("player", mObj.optString("type", "m3u8"))
                        serversList.add(
                            ServerConfig(
                                name = "Server 1",
                                type = fallbackType,
                                url = fallbackUrl,
                                priority = 1,
                                enabled = true
                            )
                        )
                    }
                }

                matchesList.add(
                    LiveMatchConfigItem(
                        id = mObj.optString("id", "match_$i"),
                        enabled = mObj.optBoolean("enabled", true),
                        hidden = mObj.optBoolean("hidden", false),
                        order = mObj.optInt("order", i + 1),
                        competition = mObj.optString("competition", ""),
                        competitionLogo = mObj.optString("competition_logo", ""),
                        homeTeam = mObj.optString("home_team", ""),
                        awayTeam = mObj.optString("away_team", ""),
                        homeLogo = mObj.optString("home_logo", ""),
                        awayLogo = mObj.optString("away_logo", ""),
                        matchDate = mObj.optString("match_date", ""),
                        matchTime = mObj.optString("match_time", ""),
                        status = mObj.optString("status", "UPCOMING"),
                        minute = mObj.optString("minute", ""),
                        thumbnail = mObj.optString("thumbnail", ""),
                        language = mObj.optString("language", "Arabic"),
                        servers = serversList.sortedBy { it.priority }
                    )
                )
            }

            LiveMatchesConfig(
                enabled = enabled,
                hidden = hidden,
                title = title,
                logo = logo,
                order = order,
                autoRefresh = autoRefresh,
                refreshInterval = refreshInterval,
                matches = matchesList.sortedBy { it.order }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[live_matches.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchMenu(context: Context) {
        val json = fetchRemoteJson("menu.json", URL_MENU) ?: return
        val parsed = parseMenu(json)
        if (parsed != null) {
            Log.d(TAG, "[menu.json] JSON Parse Success")
            saveToCache(context, "cache_menu", json, "menu.json")
            _menu.value = parsed
            Log.d(TAG, "[menu.json] Configuration Applied")
        }
    }

    private fun parseMenu(json: String): List<MenuItemConfig>? {
        return try {
            val list = mutableListOf<MenuItemConfig>()
            val trimmed = json.trim()
            val arr = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                if (root.has("menu")) root.getJSONArray("menu") else JSONArray()
            }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MenuItemConfig(
                        title = obj.optString("title", ""),
                        icon = obj.optString("icon", ""),
                        enabled = obj.optBoolean("enabled", true),
                        hidden = obj.optBoolean("hidden", false),
                        order = obj.optInt("order", i + 1),
                        action = obj.optString("action", obj.optString("title", "")),
                        url = obj.optString("url", "")
                    )
                )
            }
            list.sortedBy { it.order }
        } catch (e: Exception) {
            Log.e(TAG, "[menu.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchMessages(context: Context) {
        val json = fetchRemoteJson("messages.json", URL_MESSAGES) ?: return
        val parsed = parseMessages(json)
        if (parsed != null) {
            Log.d(TAG, "[messages.json] JSON Parse Success")
            saveToCache(context, "cache_messages", json, "messages.json")
            _messages.value = parsed
            Log.d(TAG, "[messages.json] Configuration Applied")
        }
    }

    private fun parseMessages(json: String): MessagesConfig? {
        return try {
            val root = JSONObject(json)

            fun parsePopup(key: String, defaultTitle: String = "", defaultMsg: String = ""): PopupMessage {
                if (!root.has(key)) return PopupMessage(title = defaultTitle, message = defaultMsg)
                val obj = root.getJSONObject(key)
                return PopupMessage(
                    enabled = obj.optBoolean("enabled", true),
                    title = obj.optString("title", defaultTitle),
                    message = obj.optString("message", defaultMsg),
                    buttonText = obj.optString("button_text", "OK"),
                    showOnce = obj.optBoolean("show_once", false),
                    yes = obj.optString("yes", "Yes"),
                    no = obj.optString("no", "No")
                )
            }

            val welcome = parsePopup("welcome", "Welcome", "Welcome to Powered by Ouskare")
            val announcement = parsePopup("announcement", "Announcement", "")
            val maintenance = parsePopup("maintenance", "Maintenance", "The application is currently under maintenance.")
            val serverOffline = parsePopup("server_offline", "Server Offline", "This channel is temporarily unavailable.")
            val playbackError = parsePopup("errors", "Playback Error", "Playback error occurred.")
            val internet = parsePopup("internet", "No Internet", "Please check your internet connection.")
            val update = parsePopup("update", "Update Available", "A new version of Powered by Ouskare is available.")
            val forceUpdate = parsePopup("force_update", "Update Required", "You must update the application to continue.")
            val exitDialog = parsePopup("exit_dialog", "Exit", "Are you sure you want to exit?")
            val customPopup = parsePopup("custom_popup", "", "")

            val toastObj = if (root.has("toast")) root.getJSONObject("toast") else null
            val toastChannel = toastObj?.optString("channel_changed", "Channel Changed") ?: "Channel Changed"
            val toastCategory = toastObj?.optString("category_changed", "Category Changed") ?: "Category Changed"

            val favObj = if (root.has("favorite")) root.getJSONObject("favorite") else null
            val favAdded = favObj?.optString("added", "Added to Favorites") ?: "Added to Favorites"
            val favRemoved = favObj?.optString("removed", "Removed from Favorites") ?: "Removed from Favorites"

            MessagesConfig(
                welcome = welcome,
                announcement = announcement,
                maintenance = maintenance,
                serverOffline = serverOffline,
                playbackError = playbackError,
                internet = internet,
                update = update,
                forceUpdate = forceUpdate,
                exitDialog = exitDialog,
                customPopup = customPopup,
                toastChannelChanged = toastChannel,
                toastCategoryChanged = toastCategory,
                toastAddedFavorite = favAdded,
                toastRemovedFavorite = favRemoved
            )
        } catch (e: Exception) {
            Log.e(TAG, "[messages.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchSettings(context: Context) {
        val json = fetchRemoteJson("settings.json", URL_SETTINGS) ?: return
        val parsed = parseSettings(json)
        if (parsed != null) {
            Log.d(TAG, "[settings.json] JSON Parse Success")
            saveToCache(context, "cache_settings", json, "settings.json")
            _settings.value = parsed
            Log.d(TAG, "[settings.json] Configuration Applied")
        }
    }

    private fun parseSettings(json: String): SettingsConfig? {
        return try {
            val root = JSONObject(json)
            val player = root.optJSONObject("player")
            val tv = root.optJSONObject("tv")
            val ui = root.optJSONObject("ui")
            val features = root.optJSONObject("features")
            val social = root.optJSONObject("social_links")

            SettingsConfig(
                autoPlay = player?.optBoolean("auto_play", true) ?: true,
                retryCount = player?.optInt("retry_count", 3) ?: 3,
                bufferTimeout = player?.optInt("buffer_timeout", 10) ?: 10,
                keepScreenOn = player?.optBoolean("keep_screen_on", true) ?: true,

                rememberLastChannel = tv?.optBoolean("remember_last_channel", true) ?: true,
                remoteNavigation = tv?.optBoolean("remote_navigation", true) ?: true,
                nextPreviousChannel = tv?.optBoolean("next_previous_channel", true) ?: true,

                gridColumns = ui?.optInt("grid_columns", 4) ?: 4,
                showLogos = ui?.optBoolean("show_logos", true) ?: true,
                showNames = ui?.optBoolean("show_names", true) ?: true,

                favoritesEnabled = features?.optBoolean("favorites", true) ?: true,
                searchEnabled = features?.optBoolean("search", true) ?: true,
                notificationsEnabled = features?.optBoolean("notifications", true) ?: true,
                aboutEnabled = features?.optBoolean("about", true) ?: true,
                shareEnabled = features?.optBoolean("share", true) ?: true,

                website = social?.optString("website", "https://tinghirtv.site") ?: "https://tinghirtv.site",
                facebook = social?.optString("facebook", "https://facebook.com/yourpage") ?: "https://facebook.com/yourpage",
                telegram = social?.optString("telegram", "https://t.me/yourchannel") ?: "https://t.me/yourchannel",
                youtube = social?.optString("youtube", "") ?: "",
                instagram = social?.optString("instagram", "") ?: "",
                tiktok = social?.optString("tiktok", "") ?: "",
                whatsapp = social?.optString("whatsapp", "") ?: "",
                contactEmail = social?.optString("contact_email", "contact@tinghirtv.site") ?: "contact@tinghirtv.site",
                supportEmail = social?.optString("support_email", "support@tinghirtv.site") ?: "support@tinghirtv.site",
                privacyPolicyUrl = root.optString("privacy_policy_url", social?.optString("privacy_policy", "") ?: ""),
                appVersion = root.optString("app_version", root.optString("version", "1.0.0"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "[settings.json] JSON Parse Failed: ${e.message}", e)
            null
        }
    }
}
