package `in`.darkseid.homeserver.data.repository

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.ComputerSystem
import oshi.hardware.GlobalMemory
import oshi.hardware.HWDiskStore
import oshi.hardware.HardwareAbstractionLayer
import oshi.hardware.NetworkIF
import oshi.hardware.Sensors
import oshi.software.os.FileSystem
import oshi.software.os.OSFileStore
import oshi.software.os.OperatingSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OshiStatsRepositoryTest {
    private lateinit var systemInfo: SystemInfo
    private lateinit var hal: HardwareAbstractionLayer
    private lateinit var processor: CentralProcessor
    private lateinit var sensors: Sensors
    private lateinit var memory: GlobalMemory
    private lateinit var os: OperatingSystem
    private lateinit var repository: OshiStatsRepository

    @BeforeTest
    fun setup() {
        systemInfo = mockk()
        hal = mockk()
        processor = mockk()
        sensors = mockk()
        memory = mockk()
        os = mockk()

        every { systemInfo.hardware } returns hal
        every { systemInfo.operatingSystem } returns os
        every { hal.processor } returns processor
        every { hal.sensors } returns sensors
        every { hal.memory } returns memory

        // Initial ticks for constructor
        every { processor.systemCpuLoadTicks } returns LongArray(CentralProcessor.TickType.entries.size) { 0L }

        repository = OshiStatsRepository(systemInfo)
    }

    @Test
    fun `getRamStats returns correct mapped data`() =
        runBlocking {
            every { memory.total } returns 16000L
            every { memory.available } returns 4000L

            val stats = repository.getRamStats()

            assertEquals(16000L, stats.total)
            assertEquals(12000L, stats.used)
            assertEquals(4000L, stats.available)
            assertEquals(75.0, stats.usagePercent)
        }

    @Test
    fun `getCpuStats returns correct mapped data`() =
        runBlocking {
            val mockTicks = LongArray(CentralProcessor.TickType.entries.size) { 100L }
            every { processor.systemCpuLoadTicks } returns mockTicks
            every { processor.getSystemCpuLoadBetweenTicks(any()) } returns 0.5
            every { sensors.cpuTemperature } returns 55.0

            val mockIdentifier = mockk<CentralProcessor.ProcessorIdentifier>()
            every { processor.processorIdentifier } returns mockIdentifier
            every { mockIdentifier.name } returns "Test CPU"
            every { processor.physicalProcessorCount } returns 4
            every { processor.logicalProcessorCount } returns 8

            val stats = repository.getCpuStats()

            assertEquals("Test CPU", stats.model)
            assertEquals(4, stats.physicalCores)
            assertEquals(8, stats.logicalCores)
            assertEquals(50.0, stats.usagePercent) // 0.5 * 100
            assertEquals(55.0, stats.temperature)
        }

    @Test
    fun `getStorageStats returns correct mapped data`() =
        runBlocking {
            val fileSystem = mockk<FileSystem>()
            every { os.fileSystem } returns fileSystem

            val fileStore = mockk<OSFileStore>()
            every { fileStore.mount } returns "/"
            every { fileStore.name } returns "root"
            every { fileStore.type } returns "ext4"
            every { fileStore.totalSpace } returns 1000L
            every { fileStore.usableSpace } returns 200L
            every { fileSystem.fileStores } returns listOf(fileStore)

            val diskStore = mockk<HWDiskStore>()
            every { diskStore.name } returns "sda"
            every { diskStore.model } returns "SSD"
            every { diskStore.size } returns 10000L
            every { diskStore.readBytes } returns 500L
            every { diskStore.writeBytes } returns 600L
            every { diskStore.transferTime } returns 10L
            every { hal.diskStores } returns listOf(diskStore)

            val stats = repository.getStorageStats()

            assertEquals(1, stats.partitions.size)
            assertEquals("/", stats.partitions[0].mount)
            assertEquals(800L, stats.partitions[0].usedSpace)
            assertEquals(80.0, stats.partitions[0].usagePercent)

            assertEquals(1, stats.disks.size)
            assertEquals("sda", stats.disks[0].name)
            assertEquals(500L, stats.disks[0].readBytes)
        }

    @Test
    fun `getNetworkStats returns correct mapped data`() =
        runBlocking {
            val net = mockk<NetworkIF>()
            every { net.name } returns "eth0"
            every { net.displayName } returns "Ethernet"
            every { net.macaddr } returns "00:00:00:00:00:00"
            every { net.iPv4addr } returns arrayOf("192.168.1.100")
            every { net.iPv6addr } returns arrayOf()
            every { net.bytesSent } returns 1000L
            every { net.bytesRecv } returns 2000L
            every { net.packetsSent } returns 10L
            every { net.packetsRecv } returns 20L
            every { net.speed } returns 1000000L
            every { net.ifOperStatus } returns NetworkIF.IfOperStatus.UP
            every { hal.networkIFs } returns listOf(net)

            val stats = repository.getNetworkStats()

            assertEquals(1, stats.interfaces.size)
            val iface = stats.interfaces[0]
            assertEquals("eth0", iface.name)
            assertEquals(1000L, iface.bytesSent)
            assertTrue(iface.isUp)
            assertEquals("192.168.1.100", iface.ipv4.first())
        }

    @Test
    fun `getSystemStats returns correct mapped data`() =
        runBlocking {
            every { os.family } returns "Linux"
            every { os.manufacturer } returns "Canonical"
            val versionInfo = mockk<OperatingSystem.OSVersionInfo>()
            every { versionInfo.version } returns "22.04"
            every { os.versionInfo } returns versionInfo
            every { os.systemUptime } returns 3600L
            every { os.processCount } returns 150
            every { os.threadCount } returns 500

            val computerSystem = mockk<ComputerSystem>()
            every { computerSystem.manufacturer } returns "Dell"
            every { computerSystem.model } returns "XPS"
            every { hal.computerSystem } returns computerSystem

            val mockIdentifier = mockk<CentralProcessor.ProcessorIdentifier>()
            every { processor.processorIdentifier } returns mockIdentifier
            every { mockIdentifier.name } returns "Intel"

            every { sensors.cpuVoltage } returns 1.2
            every { sensors.fanSpeeds } returns intArrayOf(2000)

            val stats = repository.getSystemStats()

            assertEquals("Linux", stats.osFamily)
            assertEquals("22.04", stats.osVersion)
            assertEquals("Dell", stats.systemManufacturer)
            assertEquals(3600L, stats.uptime)
            assertEquals(1.2, stats.cpuVoltage)
            assertEquals(2000, stats.fanSpeeds.first())
        }

    @Test
    fun `getRamStats handles zero total memory`() =
        runBlocking {
            every { memory.total } returns 0L
            every { memory.available } returns 0L

            val stats = repository.getRamStats()

            assertEquals(0L, stats.total)
            assertEquals(0.0, stats.usagePercent)
        }

    @Test
    fun `getStorageStats handles zero total space`() =
        runBlocking {
            val fileSystem = mockk<FileSystem>()
            every { os.fileSystem } returns fileSystem

            val fileStore = mockk<OSFileStore>()
            every { fileStore.mount } returns "/"
            every { fileStore.name } returns "root"
            every { fileStore.type } returns "ext4"
            every { fileStore.totalSpace } returns 0L
            every { fileStore.usableSpace } returns 0L
            every { fileSystem.fileStores } returns listOf(fileStore)

            every { hal.diskStores } returns emptyList()

            val stats = repository.getStorageStats()

            assertEquals(1, stats.partitions.size)
            assertEquals(0L, stats.partitions[0].totalSpace)
            assertEquals(0.0, stats.partitions[0].usagePercent)
        }
}
