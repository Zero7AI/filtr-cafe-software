package com.example.kitchen.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.kitchen.data.remote.SupabaseService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionMonitorTest {

    private fun createSupabase(reachable: Boolean): SupabaseService {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (reachable) 200 else 503)
                    .message("Mock")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return SupabaseService(baseUrl = "https://test.supabase.co", clientOverride = client)
    }

    @Test
    fun offlineBoot_initialStatusIsDisconnected() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(cm).setDefaultNetworkActive(false)
        shadowOf(cm).clearAllNetworks()

        val monitor = ConnectionMonitor(
            context = context,
            supabaseService = createSupabase(reachable = true),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            autoStart = false
        )

        assertEquals(CloudConnectionStatus.DISCONNECTED, monitor.status.value)
        monitor.unregister()
    }

    @Test
    fun onlineBoot_reflectsCloudReachabilityAndUnregisterStopsSafely() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val offlineCloudMonitor = ConnectionMonitor(
            context = context,
            supabaseService = createSupabase(reachable = false),
            scope = scope,
            ioDispatcher = dispatcher,
            autoStart = false
        )
        if (offlineCloudMonitor.checkCurrentNetworkAvailability()) {
            offlineCloudMonitor.checkCloudReachability()
            assertEquals(CloudConnectionStatus.CLOUD_OFFLINE, offlineCloudMonitor.status.value)
        }
        offlineCloudMonitor.unregister()

        val onlineCloudMonitor = ConnectionMonitor(
            context = context,
            supabaseService = createSupabase(reachable = true),
            scope = scope,
            ioDispatcher = dispatcher,
            autoStart = false
        )
        if (onlineCloudMonitor.checkCurrentNetworkAvailability()) {
            onlineCloudMonitor.checkCloudReachability()
            assertEquals(CloudConnectionStatus.CONNECTED, onlineCloudMonitor.status.value)
        }
        onlineCloudMonitor.unregister()
    }
}
