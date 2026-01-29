package `in`.darkseid.homeserver.domain.repository

import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.NetworkStats
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.models.StorageStats
import `in`.darkseid.homeserver.domain.models.SystemStats

interface StatsRepository {
    suspend fun getCpuStats(): CpuStats

    suspend fun getRamStats(): RamStats

    suspend fun getStorageStats(): StorageStats

    suspend fun getNetworkStats(): NetworkStats

    suspend fun getSystemStats(): SystemStats
}
