package com.intersvyaz.detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.intersvyaz.detection.Constants.WIFI_COUNT_SIGNAL_LEVELS
import com.intersvyaz.detection.Constants.WIFI_SIGNAL_LEVEL_GOOD
import com.intersvyaz.detection.Constants.WIFI_SIGNAL_LEVEL_HIGH
import com.intersvyaz.detection.Constants.WIFI_SIGNAL_LEVEL_SLOW
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NetworkManager(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    val networkStatus = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                launch { send(NetworkStatus.Available) }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                launch { send(NetworkStatus.Unavailable) }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                launch { send(NetworkStatus.Lost) }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val wifiConnectionLevel = connectivityLevelWifi()
                val internetExist = hasInternetAccess()
                launch {
                    send(
                        NetworkStatus.CapabilitiesChanging(
                            wifiConnectionLevel,
                            internetExist,
                        )
                    )
                }
            }
        }

        val networkRequest = NetworkRequest
            .Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()

    private fun connectivityLevelWifi(): ConnectionLevel? {
        val wifiConnectionInfo = wifiManager?.connectionInfo?.rssi ?: return null
        val wifiConnectionLevel = WifiManager.calculateSignalLevel(
            wifiConnectionInfo,
            WIFI_COUNT_SIGNAL_LEVELS
        )
        return when (wifiConnectionLevel) {
            WIFI_SIGNAL_LEVEL_SLOW -> ConnectionLevel.WIFI_LEVEL_SLOW
            WIFI_SIGNAL_LEVEL_GOOD -> ConnectionLevel.WIFI_LEVEL_GOOD
            WIFI_SIGNAL_LEVEL_HIGH -> ConnectionLevel.WIFI_LEVEL_HIGH
            else -> ConnectionLevel.WIFI_LEVEL_WEAK
        }
    }

    private fun hasInternetAccess(): Boolean {
        if (connectivityManager?.activeNetwork != null) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    setRequestProperty("Connection", "close")
                    connectTimeout = 1000
                    connect()
                }
                return connection.responseCode == 200
            } catch (exception: IOException) {
                Log.e(TAG, "Error checking internet connection")
            }
        } else {
            Log.e(TAG, "No internet connection")
        }
        return false
    }

    companion object {
        private const val url = "https://www.google.com/"
        private val TAG = NetworkManager::class.java.simpleName
    }
}