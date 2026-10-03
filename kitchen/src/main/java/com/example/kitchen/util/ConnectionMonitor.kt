package com.example.kitchen.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.kitchen.data.remote.SupabaseService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CloudConnectionStatus {
    CONNECTED,      // Internet OK & Cloud backend reachable
    CLOUD_OFFLINE,  // Internet OK but Cloud unreachable
    DISCONNECTED    // No Internet / Offline
}

class ConnectionMonitor(
    private val context: Context,
    private val supabaseService: SupabaseService,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(CloudConnectionStatus.CONNECTED)
    val status: StateFlow<CloudConnectionStatus> = _status.asStateFlow()

    private var isNetworkAvailable = true

    init {
        registerNetworkCallback()
        startCloudHealthCheck()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            _status.value = CloudConnectionStatus.DISCONNECTED
            return
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isNetworkAvailable = true
                    checkCloudReachability()
                }

                override fun onLost(network: Network) {
                    isNetworkAvailable = false
                    _status.value = CloudConnectionStatus.DISCONNECTED
                }
            })
        } catch (e: Exception) {
            // Fallback: check active network info
            val active = connectivityManager.activeNetworkInfo
            isNetworkAvailable = active != null && active.isConnected
            if (!isNetworkAvailable) {
                _status.value = CloudConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun startCloudHealthCheck() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isNetworkAvailable) {
                    checkCloudReachability()
                } else {
                    _status.value = CloudConnectionStatus.DISCONNECTED
                }
                delay(15_000L) // Check every 15s
            }
        }
    }

    private fun checkCloudReachability() {
        scope.launch(Dispatchers.IO) {
            val reachable = supabaseService.checkReachability()
            _status.value = when {
                !isNetworkAvailable -> CloudConnectionStatus.DISCONNECTED
                reachable -> CloudConnectionStatus.CONNECTED
                else -> CloudConnectionStatus.CLOUD_OFFLINE
            }
        }
    }
}
