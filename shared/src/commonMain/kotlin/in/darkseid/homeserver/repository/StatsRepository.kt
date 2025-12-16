package `in`.darkseid.homeserver.repository

import `in`.darkseid.homeserver.models.CpuStats

interface StatsRepository {
    suspend fun getCpuStats(): CpuStats
}
