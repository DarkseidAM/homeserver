package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.DiskDeviceStat
import `in`.darkseid.homeserver.domain.models.InterfaceStat
import `in`.darkseid.homeserver.domain.models.NetworkStats
import `in`.darkseid.homeserver.domain.models.PartitionStat
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.models.StorageStats
import `in`.darkseid.homeserver.domain.models.SystemStats
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.utils.withName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import oshi.hardware.NetworkIF

class OshiStatsRepository(
    systemInfo: SystemInfo,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StatsRepository {
    private val hardware = systemInfo.hardware
    private val os = systemInfo.operatingSystem
    private val processor = hardware.processor
    private val sensors = hardware.sensors
    private val memory = hardware.memory

    private var prevTicks = processor.systemCpuLoadTicks

    override suspend fun getCpuStats() =
        withContext(dispatcher.withName("Oshi-CpuStats")) {
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
        withContext(dispatcher.withName("Oshi-RamStats")) {
            val total = memory.total
            val available = memory.available
            val used = total - available

            RamStats(
                total = total,
                used = used,
                available = available,
                usagePercent = if (total > 0) used.toDouble() / total.toDouble() * 100 else 0.0,
            )
        }

    override suspend fun getStorageStats() =
        withContext(dispatcher.withName("Oshi-StorageStats")) {
            val fileStores = os.fileSystem.fileStores
            val disks = hardware.diskStores

            val partitions =
                fileStores.map { fs ->
                    val total = fs.totalSpace
                    val usable = fs.usableSpace
                    val used = total - usable
                    PartitionStat(
                        mount = fs.mount,
                        name = fs.name,
                        type = fs.type,
                        totalSpace = total,
                        usedSpace = used,
                        availableSpace = usable,
                        usagePercent = if (total > 0) used.toDouble() / total * 100 else 0.0,
                    )
                }

            val diskStats =
                disks.map { disk ->
                    DiskDeviceStat(
                        name = disk.name,
                        model = disk.model,
                        size = disk.size,
                        readBytes = disk.readBytes,
                        writeBytes = disk.writeBytes,
                        transferTime = disk.transferTime,
                    )
                }

            StorageStats(partitions, diskStats)
        }

    override suspend fun getNetworkStats() =
        withContext(dispatcher.withName("Oshi-NetworkStats")) {
            val ifs = hardware.networkIFs
            val interfaces =
                ifs.map { net ->
                    InterfaceStat(
                        name = net.name,
                        displayName = net.displayName,
                        macAddress = net.macaddr,
                        ipv4 = net.iPv4addr.toList(),
                        ipv6 = net.iPv6addr.toList(),
                        bytesSent = net.bytesSent,
                        bytesRecv = net.bytesRecv,
                        packetsSent = net.packetsSent,
                        packetsRecv = net.packetsRecv,
                        speed = net.speed,
                        isUp = net.ifOperStatus == NetworkIF.IfOperStatus.UP,
                    )
                }
            NetworkStats(interfaces)
        }

    override suspend fun getSystemStats() =
        withContext(dispatcher.withName("Oshi-SystemStats")) {
            SystemStats(
                osFamily = os.family,
                osManufacturer = os.manufacturer,
                osVersion = os.versionInfo.version,
                systemManufacturer = hardware.computerSystem.manufacturer,
                systemModel = hardware.computerSystem.model,
                processorName = processor.processorIdentifier.name,
                uptime = os.systemUptime,
                processCount = os.processCount,
                threadCount = os.threadCount,
                cpuVoltage = sensors.cpuVoltage,
                fanSpeeds = sensors.fanSpeeds.toList(),
            )
        }

    companion object {
        private const val CPU_LOAD_FACTOR = 100.0
    }
}
