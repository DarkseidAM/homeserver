package `in`.darkseid.homeserver.domain.repository

import `in`.darkseid.homeserver.domain.models.CpuStats

interface StatsRepository {
    suspend fun getCpuStats(): CpuStats
}
