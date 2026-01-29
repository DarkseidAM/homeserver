package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class NetworkStats(
    val interfaces: List<InterfaceStat>,
)

@Serializable
data class InterfaceStat(
    val name: String,
    val displayName: String,
    val macAddress: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val bytesSent: Long,
    val bytesRecv: Long,
    val packetsSent: Long,
    val packetsRecv: Long,
    val speed: Long, // bits per second
    val isUp: Boolean,
)
