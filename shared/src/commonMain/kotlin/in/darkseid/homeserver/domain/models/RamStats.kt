package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class RamStats(
    val total: Long, // In bytes
    val used: Long, // In bytes
    val available: Long, // In bytes
    val usagePercent: Double, // 0.0 to 100.0
)
