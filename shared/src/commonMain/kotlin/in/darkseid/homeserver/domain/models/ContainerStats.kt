package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable
@Serializable
data class ContainerStats(
    val id: String,
    val name: String,
    val cpuPercent: Double,
    val memoryUsageBytes: Long,
    val memoryLimitBytes: Long,
    val state: String // "running", "exited"
)

@Serializable
data class FullSystemSnapshot(
    val timestamp: Long,
    val cpu: CpuStats,
    val ram: RamStats,
    val containers: List<ContainerStats>
)
