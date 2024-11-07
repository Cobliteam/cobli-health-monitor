package co.cobli.healthmonitor.model.data

import co.cobli.healthmonitor.model.DataReader.Companion.UNKNOWN_LONG

data class NetworkConsumption(
    val received: Long,
    val transmitted: Long,
    val total: Long = if (received >= 0 && transmitted >= 0) {
        received + transmitted
    } else UNKNOWN_LONG
)