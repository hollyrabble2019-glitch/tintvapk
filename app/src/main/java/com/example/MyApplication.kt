package com.example

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Pre-create WebView cache directories to prevent Chromium opendir errors on startup.
        // We set wide permissions (read, write, execute for all) so that the isolated/sandboxed WebView renderer
        // processes can read and write to these cache folders without permission issues.
        try {
            val dirs = listOf(
                java.io.File(cacheDir, "WebView"),
                java.io.File(cacheDir, "WebView/Default"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            )
            for (dir in dirs) {
                if (!dir.exists()) {
                    dir.mkdir()
                }
                dir.setWritable(true, false)
                dir.setReadable(true, false)
                dir.setExecutable(true, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
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
}
