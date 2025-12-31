package `in`.darkseid.homeserver.domain.repository

import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.RamStats

interface StatsRepository {
    suspend fun getCpuStats(): CpuStats
    suspend fun getRamStats(): RamStats
}
