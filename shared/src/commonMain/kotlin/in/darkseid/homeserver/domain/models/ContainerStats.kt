package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class ContainerStats(
    val id: String,
    val name: String,
    val cpuPercent: Double,
    val memoryUsageBytes: Long,
    val memoryLimitBytes: Long,
    val state: String, // "running", "exited"
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0,
    val blockReadBytes: Long = 0,
    val blockWriteBytes: Long = 0,
    val pids: Long = 0,
    val cpuThrottled: Boolean = false,
    val memoryMaxUsageBytes: Long = 0,
)

@Serializable
data class FullSystemSnapshot(
    val timestamp: Long,
    val cpu: CpuStats,
    val ram: RamStats,
    val containers: List<ContainerStats>,
)
