package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NetworkStatus {
    object Wifi : NetworkStatus
    object Mobile : NetworkStatus
    object Ethernet : NetworkStatus
    object NoConnection : NetworkStatus
}

object NetworkMonitor {
    private const val TAG = "NetworkMonitor"
    private lateinit var connectivityManager: ConnectivityManager
    
    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.NoConnection)
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val updatedStatus = getCurrentConnectivity()
            Log.d(TAG, "Network became available. Status: $updatedStatus")
            _status.value = updatedStatus
        }

        override fun onLost(network: Network) {
            val updatedStatus = getCurrentConnectivity()
            Log.d(TAG, "Network was lost. Status: $updatedStatus")
            _status.value = updatedStatus
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val updatedStatus = getCurrentConnectivity()
            Log.d(TAG, "Network capabilities changed. Status: $updatedStatus")
            _status.value = updatedStatus
        }
    }

    fun init(context: Context) {
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            _status.value = getCurrentConnectivity()

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "NetworkMonitor initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
            CrashlyticsHelper.recordNonFatal(e)
        }
    }

    private fun getCurrentConnectivity(): NetworkStatus {
        if (!::connectivityManager.isInitialized) return NetworkStatus.NoConnection
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.NoConnection
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.NoConnection

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.Mobile
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.Ethernet
            else -> NetworkStatus.NoConnection
        }
    }
}
