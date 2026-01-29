package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class StorageStats(
    val partitions: List<PartitionStat>,
    val disks: List<DiskDeviceStat>,
)

@Serializable
data class PartitionStat(
    val mount: String,
    val name: String,
    val type: String,
    val totalSpace: Long, // bytes
    val usedSpace: Long, // bytes
    val availableSpace: Long, // bytes
    val usagePercent: Double,
)

@Serializable
data class DiskDeviceStat(
    val name: String,
    val model: String,
    val size: Long,
    val readBytes: Long,
    val writeBytes: Long,
    val transferTime: Long, // ms
)
