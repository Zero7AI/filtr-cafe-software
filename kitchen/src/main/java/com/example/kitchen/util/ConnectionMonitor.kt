package com.example.kitchen.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.kitchen.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CloudConnectionStatus {
    CONNECTED,      // Internet OK & Cloud backend reachable
    CLOUD_OFFLINE,  // Internet OK but Cloud unreachable
    DISCONNECTED    // No Internet / Offline
}

class ConnectionMonitor(
    context: Context,
    private val supabaseService: SupabaseService,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    autoStart: Boolean = true
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile
    private var isNetworkAvailable: Boolean = checkCurrentNetworkAvailability()

    private val _status = MutableStateFlow(
        if (isNetworkAvailable) CloudConnectionStatus.CONNECTED else CloudConnectionStatus.DISCONNECTED
    )
    val status: StateFlow<CloudConnectionStatus> = _status.asStateFlow()

    private val reachabilityMutex = Mutex()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var healthCheckJob: Job? = null
    private var reachabilityJob: Job? = null

    init {
        if (autoStart) {
            registerNetworkCallback()
            startCloudHealthCheck()
        }
    }

    internal fun checkCurrentNetworkAvailability(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager
        if (cm == null) {
            isNetworkAvailable = false
            _status.value = CloudConnectionStatus.DISCONNECTED
            return
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkAvailable = true
                triggerReachabilityCheck()
            }

            override fun onLost(network: Network) {
                val stillConnected = checkCurrentNetworkAvailability()
                isNetworkAvailable = stillConnected
                if (!stillConnected) {
                    _status.value = CloudConnectionStatus.DISCONNECTED
                } else {
                    triggerReachabilityCheck()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet != isNetworkAvailable) {
                    isNetworkAvailable = hasInternet
                    if (hasInternet) {
                        triggerReachabilityCheck()
                    } else {
                        _status.value = CloudConnectionStatus.DISCONNECTED
                    }
                }
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
            isNetworkAvailable = checkCurrentNetworkAvailability()
            if (!isNetworkAvailable) {
                _status.value = CloudConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun startCloudHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch(ioDispatcher) {
            while (isActive) {
                if (isNetworkAvailable) {
                    checkCloudReachability()
                } else {
                    _status.value = CloudConnectionStatus.DISCONNECTED
                }
                delay(15_000L)
            }
        }
    }

    private fun triggerReachabilityCheck() {
        reachabilityJob?.cancel()
        reachabilityJob = scope.launch(ioDispatcher) {
            checkCloudReachability()
        }
    }

    internal suspend fun checkCloudReachability() {
        reachabilityMutex.withLock {
            if (!isNetworkAvailable) {
                _status.value = CloudConnectionStatus.DISCONNECTED
                return
            }
            val reachable = supabaseService.checkReachability()
            _status.value = when {
                !isNetworkAvailable -> CloudConnectionStatus.DISCONNECTED
                reachable -> CloudConnectionStatus.CONNECTED
                else -> CloudConnectionStatus.CLOUD_OFFLINE
            }
        }
    }

    fun unregister() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        reachabilityJob?.cancel()
        reachabilityJob = null
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Already unregistered
        }
    }
}
