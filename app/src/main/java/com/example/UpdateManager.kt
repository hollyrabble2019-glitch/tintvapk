package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "remote_update_prefs"
    private const val KEY_CONFIG_JSON = "update_config_json"
    
    private const val REMOTE_URL = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/update.json"

    class UpdateConfig(
        val enabled: Boolean = true,
        val force: Boolean = false,
        val version: String = "1.0.0",
        val title: String = "تحديث | Update",
        val message: String = "يتوفر إصدار جديد من Powered by Ouskare. يرجى تحديث التطبيق للاستفادة من أحدث التحسينات.",
        val buttonText: String = "تحديث | Update",
        val buttonColor: String = "#7B1FA2",
        val textColor: String = "#FFFFFF",
        val titleColor: String = "#00CFCF",
        val backgroundColor: String = "#1E1E1E",
        val updateUrl: String = "https://github.com/sakifooo/TinghirTV/releases/download/v1.0.0/TinghirTV.apk"
    )

    // Current config state flow
    private val _config = MutableStateFlow<UpdateConfig?>(null)
    val config: StateFlow<UpdateConfig?> = _config

    // Whether we should display the update dialog right now
    var showDialog by mutableStateOf(false)
        private set

    // Track if user has already dismissed the update dialog during this app session
    private var hasDialogBeenDismissedThisSession = false

    fun init(context: Context) {
        Log.d(TAG, "Initializing UpdateManager...")
        CoroutineScope(Dispatchers.IO).launch {
            // Load cache on background thread
            loadFromCache(context)

            // Perform version check and check for update on background thread
            val installedVersion = getAppVersionName(context)
            val currentConfig = _config.value
            
            val updateAvailable = if (currentConfig != null && currentConfig.enabled) {
                if (hasDialogBeenDismissedThisSession && !currentConfig.force) {
                    false
                } else {
                    isVersionNewer(installedVersion, currentConfig.version)
                }
            } else {
                false
            }

            withContext(Dispatchers.Main) {
                if (currentConfig != null && currentConfig.enabled) {
                    showDialog = updateAvailable
                    if (updateAvailable) {
                        Log.d(TAG, "Update Available on Init")
                    }
                } else {
                    showDialog = false
                }
            }
            
            // Fetch remote config on background thread
            fetchRemoteConfig(context)
        }
    }

    private fun loadFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (jsonStr != null) {
            Log.d(TAG, "Loaded cached update configuration: $jsonStr")
            val parsed = parseConfig(jsonStr)
            _config.value = parsed
        } else {
            Log.d(TAG, "No cached update configuration found. Defaulting to system disabled.")
            // No cached config -> disabled by default
            _config.value = null
        }
    }

    private fun saveToCache(context: Context, jsonStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG_JSON, jsonStr).apply()
        Log.d(TAG, "Saved update configuration to cache.")
    }

    private fun parseConfig(jsonStr: String): UpdateConfig? {
        return try {
            val root = JSONObject(jsonStr)
            val updateObj = if (root.has("update")) root.getJSONObject("update") else root
            UpdateConfig(
                enabled = updateObj.optBoolean("enabled", true),
                force = updateObj.optBoolean("force", false),
                version = updateObj.optString("version", "1.0.0"),
                title = updateObj.optString("title", "تحديث | Update"),
                message = updateObj.optString("message", "يتوفر إصدار جديد من Tinghir TV. يرجى تحديث التطبيق للاستفادة من أحدث التحسينات."),
                buttonText = updateObj.optString("button_text", "تحديث | Update"),
                buttonColor = updateObj.optString("button_color", "#7B1FA2"),
                textColor = updateObj.optString("text_color", "#FFFFFF"),
                titleColor = updateObj.optString("title_color", "#00CFCF"),
                backgroundColor = updateObj.optString("background_color", "#1E1E1E"),
                updateUrl = updateObj.optString("update_url", "https://github.com/sakifooo/TinghirTV/releases/download/v1.0.0/TinghirTV.apk")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing update configuration JSON: ${e.message}", e)
            CrashlyticsHelper.recordNonFatal(e)
            null
        }
    }

    private suspend fun fetchRemoteConfig(context: Context) {
        Log.d(TAG, "Remote Update JSON Download Started")
        Log.d(TAG, "Fetching remote update configuration from $REMOTE_URL")
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
                    Log.d(TAG, "Remote Update JSON Download Success")
                    Log.d(TAG, "Successfully fetched remote update configuration: $bodyString")
                    val parsed = parseConfig(bodyString)
                    if (parsed != null) {
                        _config.value = parsed
                        saveToCache(context, bodyString)
                        // Trigger check on background thread to avoid main-thread packageManager lookup
                        checkForUpdate(context)
                    }
                } else {
                    throw IOException("Empty response body received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote update configuration: ${e.message}.", e)
            CrashlyticsHelper.recordNonFatal(e)
        }
    }

    fun checkForUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentConfig = _config.value
            if (currentConfig == null || !currentConfig.enabled) {
                Log.d(TAG, "Update Disabled")
                Log.d(TAG, "Update system is disabled or configuration not available.")
                withContext(Dispatchers.Main) {
                    showDialog = false
                }
                return@launch
            }

            if (hasDialogBeenDismissedThisSession && !currentConfig.force) {
                Log.d(TAG, "Update dialog was already dismissed in this session and is not forced. Skipping.")
                withContext(Dispatchers.Main) {
                    showDialog = false
                }
                return@launch
            }

            val currentVersion = getAppVersionName(context)
            val remoteVersion = currentConfig.version

            Log.d(TAG, "Checking version compatibility: Installed version = $currentVersion, Remote version = $remoteVersion")
            val isNewer = isVersionNewer(currentVersion, remoteVersion)
            withContext(Dispatchers.Main) {
                if (isNewer) {
                    Log.d(TAG, "Update Available")
                    Log.d(TAG, "Newer version available ($remoteVersion). Triggering update dialog.")
                    showDialog = true
                    Log.d(TAG, "Update Dialog Displayed")
                } else {
                    Log.d(TAG, "Update Version Equal")
                    Log.d(TAG, "Installed version ($currentVersion) is up-to-date compared to remote ($remoteVersion).")
                    showDialog = false
                }
            }
        }
    }

    fun dismissDialog() {
        Log.d(TAG, "Dismissing update dialog.")
        showDialog = false
        hasDialogBeenDismissedThisSession = true
    }

    fun performUpdate(context: Context) {
        val currentConfig = _config.value ?: return
        Log.d(TAG, "Performing update: redirecting to ${currentConfig.updateUrl}")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentConfig.updateUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open update URL: ${e.message}", e)
            CrashlyticsHelper.recordNonFatal(e)
        }
    }

    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version name from PackageInfo, fallback to BuildConfig: ${e.message}")
            CrashlyticsHelper.recordNonFatal(e)
            BuildConfig.VERSION_NAME
        }
    }

    fun isVersionNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = currentVersion.split(".").mapNotNull { it.trim().toIntOrNull() }
        val remoteParts = remoteVersion.split(".").mapNotNull { it.trim().toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val remotePart = remoteParts.getOrElse(i) { 0 }
            if (remotePart > currentPart) return true
            if (currentPart > remotePart) return false
        }
        return false
    }
}
