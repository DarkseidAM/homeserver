package `in`.darkseid.homeserver.data.repository

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.GlobalMemory
import oshi.hardware.HardwareAbstractionLayer
import oshi.hardware.Sensors
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OshiStatsRepositoryTest {
    private lateinit var systemInfo: SystemInfo
    private lateinit var hal: HardwareAbstractionLayer
    private lateinit var processor: CentralProcessor
    private lateinit var sensors: Sensors
    private lateinit var memory: GlobalMemory
    private lateinit var repository: OshiStatsRepository

    @BeforeTest
    fun setup() {
        systemInfo = mockk()
        hal = mockk()
        processor = mockk()
        sensors = mockk()
        memory = mockk()

        every { systemInfo.hardware } returns hal
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
}
