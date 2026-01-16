package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatsModelsTest {
    @Test
    fun testSerializers() {
        assertNotNull(CpuStats.serializer())
        assertNotNull(RamStats.serializer())
        assertNotNull(ContainerStats.serializer())
        assertNotNull(FullSystemSnapshot.serializer())

        assertEquals("in.darkseid.homeserver.domain.models.CpuStats", CpuStats.serializer().descriptor.serialName)
    }

    @Test
    fun testCpuStatsSerialization() {
        val cpu =
            CpuStats(
                model = "Intel Core i9",
                physicalCores = 8,
                logicalCores = 16,
                usagePercent = 15.5,
                temperature = 45.0,
            )
        val json = Json.encodeToString(cpu)
        val decoded = Json.decodeFromString<CpuStats>(json)
        assertEquals(cpu, decoded)
    }

    @Test
    fun testRamStatsSerialization() {
        val ram =
            RamStats(
                total = 16000000000L,
                used = 8000000000L,
                available = 8000000000L,
                usagePercent = 50.0,
            )
        val json = Json.encodeToString(ram)
        val decoded = Json.decodeFromString<RamStats>(json)
        assertEquals(ram, decoded)
    }

    @Test
    fun testContainerStatsSerialization() {
        val container =
            ContainerStats(
                id = "123",
                name = "nginx",
                cpuPercent = 0.5,
                memoryUsageBytes = 1024L,
                memoryLimitBytes = 2048L,
                state = "running",
            )
        val json = Json.encodeToString(container)
        val decoded = Json.decodeFromString<ContainerStats>(json)
        assertEquals(container, decoded)
    }

    @Test
    fun testFullSystemSnapshotSerialization() {
        val cpu = CpuStats("M1", 8, 8, 10.0, 30.0)
        val ram = RamStats(100L, 50L, 50L, 50.0)
        val container = ContainerStats("1", "test", 0.1, 100L, 200L, "running")
        val snapshot =
            FullSystemSnapshot(
                timestamp = 123456789L,
                cpu = cpu,
                ram = ram,
                containers = listOf(container),
            )

        val json = Json.encodeToString(snapshot)
        val decoded = Json.decodeFromString<FullSystemSnapshot>(json)
        assertEquals(snapshot, decoded)

        // Verify getters for FullSystemSnapshot and children
        assertEquals(123456789L, snapshot.timestamp)
        assertEquals(cpu, snapshot.cpu)
        assertEquals(ram, snapshot.ram)
        assertEquals(listOf(container), snapshot.containers)

        // CpuStats getters
        assertEquals("M1", cpu.model)
        assertEquals(8, cpu.physicalCores)
        assertEquals(8, cpu.logicalCores)
        assertEquals(10.0, cpu.usagePercent)
        assertEquals(30.0, cpu.temperature)

        // RamStats getters
        assertEquals(100L, ram.total)
        assertEquals(50L, ram.used)
        assertEquals(50L, ram.available)
        assertEquals(50.0, ram.usagePercent)

        // ContainerStats getters
        assertEquals("1", container.id)
        assertEquals("test", container.name)
        assertEquals(0.1, container.cpuPercent)
        assertEquals(100L, container.memoryUsageBytes)
        assertEquals(200L, container.memoryLimitBytes)
        assertEquals("running", container.state)
    }

    @Test
    fun testDataClassMethods() {
        val cpu1 = CpuStats("M1", 8, 8, 10.0, 30.0)
        val cpu2 = cpu1.copy()
        val cpu3 = cpu1.copy(model = "M2")

        assertEquals(cpu1, cpu2)
        assertEquals(cpu1.hashCode(), cpu2.hashCode())
        assertTrue(cpu1.toString().contains("M1"))

        // Test componentN
        assertEquals("M1", cpu1.component1())
        assertEquals(8, cpu1.component2())

        // Inequality
        assertTrue(cpu1 != cpu3)

        // RamStats
        val ram1 = RamStats(100L, 50L, 50L, 50.0)
        val ram2 = ram1.copy()
        assertEquals(ram1, ram2)
        assertEquals(ram1.hashCode(), ram2.hashCode())
        assertTrue(ram1.toString().contains("100"))

        // ContainerStats
        val container1 = ContainerStats("1", "test", 0.1, 100L, 200L, "running")
        val container2 = container1.copy()
        assertEquals(container1, container2)
        assertEquals(container1.hashCode(), container2.hashCode())
        assertTrue(container1.toString().contains("test"))
    }
}
