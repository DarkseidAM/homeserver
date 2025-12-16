package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class OshiStatsRepository(systemInfo: SystemInfo) : StatsRepository {
    private val hardware = systemInfo.hardware
    private val processor = hardware.processor
    private val sensors = hardware.sensors

    private var prevTicks = processor.systemCpuLoadTicks
    override suspend fun getCpuStats() = withContext(Dispatchers.IO) {
        val currentTicks = processor.systemCpuLoadTicks
        val load = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100
        prevTicks = currentTicks

        val temp = sensors.cpuTemperature

        CpuStats(
            model = processor.processorIdentifier.name,
            physicalCores = processor.physicalProcessorCount,
            logicalCores = processor.logicalProcessorCount,
            usagePercent = load,
            temperature = temp
        )
    }
}
