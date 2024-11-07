package co.cobli.healthmonitor.model.broadcasts

interface BroadcastListener {
    fun onBroadcastReceived(action: String)
}