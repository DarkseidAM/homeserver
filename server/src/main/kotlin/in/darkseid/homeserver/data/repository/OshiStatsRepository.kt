package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class OshiStatsRepository(
    systemInfo: SystemInfo,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StatsRepository {
    private val hardware = systemInfo.hardware
    private val processor = hardware.processor
    private val sensors = hardware.sensors
    private val memory = hardware.memory

    private var prevTicks = processor.systemCpuLoadTicks

    override suspend fun getCpuStats() =
        withContext(dispatcher) {
            val currentTicks = processor.systemCpuLoadTicks
            val load = processor.getSystemCpuLoadBetweenTicks(prevTicks) * CPU_LOAD_FACTOR
            prevTicks = currentTicks

            val temp = sensors.cpuTemperature

            CpuStats(
                model = processor.processorIdentifier.name,
                physicalCores = processor.physicalProcessorCount,
                logicalCores = processor.logicalProcessorCount,
                usagePercent = load,
                temperature = temp,
            )
        }

    override suspend fun getRamStats() =
        withContext(dispatcher) {
            val total = memory.total
            val available = memory.available
            val used = total - available

            RamStats(
                total = total,
                used = used,
                available = available,
                usagePercent = used.toDouble() / total.toDouble() * 100,
            )
        }

    companion object {
        private const val CPU_LOAD_FACTOR = 100.0
    }
}
