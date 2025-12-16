package `in`.darkseid.homeserver.models

import kotlinx.serialization.Serializable

@Serializable
data class CpuStats(
    val model: String,
    val physicalCores: Int,
    val logicalCores: Int,
    val usagePercent: Double, // 0.0 to 100.0
    val temperature: Double   // In Celsius (if available)
)
