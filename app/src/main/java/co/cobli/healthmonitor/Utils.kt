package co.cobli.healthmonitor

object Utils {
    fun toHexString(byteArray: ByteArray): String {
        return byteArray.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun toByteArray(hexString: String): ByteArray {
        require(hexString.length % 2 == 0) { "Hex string must have an even length" }

        return ByteArray(hexString.length / 2) { index ->
            hexString.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}