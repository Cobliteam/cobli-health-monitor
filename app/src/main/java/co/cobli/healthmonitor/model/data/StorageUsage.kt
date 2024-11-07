package co.cobli.healthmonitor.model.data

import co.cobli.healthmonitor.model.DataReader.Companion.UNKNOWN_FLOAT
import java.math.BigDecimal
import java.math.RoundingMode

data class StorageUsage(
    val total: Long,
    val available: Long,
    val usagePercentage: Float = if (total > 0 && available >= 0) {
        BigDecimal(total - available)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(total), 2, RoundingMode.HALF_UP)
            .toFloat()
    } else UNKNOWN_FLOAT
)