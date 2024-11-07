package co.cobli.healthmonitor.model.data

import co.cobli.cameraMessage.protos.NetworkTypePB

data class NetworkStatus(
    val connected: Boolean,
    val networkName: String,
    val networkType: NetworkTypePB =
        when (networkName) {
            "WIFI" -> NetworkTypePB.WIFI
            "MOBILE" -> NetworkTypePB.MOBILE
            else -> NetworkTypePB.UNKNOWN_NETWORK
        }
)