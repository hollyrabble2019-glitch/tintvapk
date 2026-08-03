package com.example

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Pre-create WebView cache directories to prevent Chromium opendir errors on startup.
        // We set wide permissions (read, write, execute for all) so that the isolated/sandboxed WebView renderer
        // processes can read and write to these cache folders without permission issues.
        ensureWebViewDirectories(this)
        
        // Initialize ad managers and lifecycle tracking
        MediationAdManager.init(this)
        
        // Initialize Firebase Crashlytics custom keys
        CrashlyticsHelper.init(this)

        // Initialize Network connectivity monitoring
        NetworkMonitor.init(this)
        
        // Initialize central Remote Configuration system (Config, Categories, Channels, Live Matches, Ads, Update, Menu, Messages, Settings)
        com.example.data.RemoteConfigManager.init(this)

        // Initialize remote update configuration system
        UpdateManager.init(this)
    }

    companion object {
        fun ensureWebViewDirectories(context: android.content.Context) {
            try {
                val cache = context.cacheDir ?: return
                val basePaths = listOf(
                    "WebView",
                    "app_webview",
                    "org.chromium.android_webview"
                )
                val subPaths = listOf(
                    "",
                    "Default",
                    "Default/HTTP Cache",
                    "Default/HTTP Cache/Code Cache",
                    "Default/HTTP Cache/Code Cache/js",
                    "Default/HTTP Cache/Code Cache/wasm",
                    "Default/Code Cache",
                    "Default/Code Cache/js",
                    "Default/Code Cache/wasm",
                    "Default/GPUCache",
                    "Default/Service Worker",
                    "Default/Service Worker/CacheStorage",
                    "Default/Service Worker/ScriptCache",
                    "Code Cache",
                    "Code Cache/js",
                    "Code Cache/wasm",
                    "ShaderCache",
                    "GrShaderCache"
                )
                for (base in basePaths) {
                    for (sub in subPaths) {
                        val dir = java.io.File(cache, if (sub.isEmpty()) base else "$base/$sub")
                        if (!dir.exists()) {
                            dir.mkdirs()
                        }
                        dir.setReadable(true, false)
                        dir.setWritable(true, false)
                        dir.setExecutable(true, false)
                        try {
                            val keep = java.io.File(dir, ".keep")
                            if (!keep.exists()) {
                                keep.createNewFile()
                            }
                            keep.setReadable(true, false)
                            keep.setWritable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
