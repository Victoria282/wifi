package com.intersvyaz.detection

sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Unavailable : NetworkStatus()
    object Lost : NetworkStatus()

    data class CapabilitiesChanging(
        private val level: ConnectionLevel?,
        private val internetExist: Boolean,
    ) : NetworkStatus() {
        fun connectionLevel() = level ?: ConnectionLevel.WIFI_LEVEL_WEAK
        fun internetConnectionExist() = internetExist
    }
}