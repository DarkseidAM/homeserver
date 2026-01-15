package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val publishing: PublishingConfig,
)

@Serializable
data class ServerConfig(
    val port: Int,
    val host: String,
)

@Serializable
data class DatabaseConfig(
    val path: String,
    val maxPoolSize: Int,
    val flushRate: Int,
)

@Serializable
data class PublishingConfig(
    val cpuStatsFrequency: Long,
    val ramStatsFrequency: Long,
)
