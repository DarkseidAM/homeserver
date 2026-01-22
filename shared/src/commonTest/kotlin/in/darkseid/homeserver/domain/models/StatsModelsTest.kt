package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
                networkRxBytes = 100L,
                networkTxBytes = 200L,
                blockReadBytes = 50L,
                blockWriteBytes = 60L,
                pids = 10L,
                cpuThrottled = true,
                memoryMaxUsageBytes = 3000L,
            )
        val json = Json.encodeToString(container)
        val decoded = Json.decodeFromString<ContainerStats>(json)
        assertEquals(container, decoded)
    }

    @Test
    fun testFullSystemSnapshotSerialization() {
        val cpu = CpuStats("M1", 8, 8, 10.0, 30.0)
        val ram = RamStats(100L, 50L, 50L, 50.0)
        val container =
            ContainerStats(
                "1",
                "test",
                0.1,
                100L,
                200L,
                "running",
                10L,
                20L,
                30L,
                40L,
                5L,
                false,
                500L,
            )
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
        assertEquals(10L, container.networkRxBytes)
        assertEquals(20L, container.networkTxBytes)
        assertEquals(30L, container.blockReadBytes)
        assertEquals(40L, container.blockWriteBytes)
        assertEquals(5L, container.pids)
        assertEquals(false, container.cpuThrottled)
        assertEquals(500L, container.memoryMaxUsageBytes)
    }

    @Test
    @Suppress("EqualsNullCall")
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
        assertNotEquals(cpu1, cpu3)

        // RamStats
        val ram1 = RamStats(100L, 50L, 50L, 50.0)
        val ram2 = ram1.copy()
        assertEquals(ram1, ram2)
        assertEquals(ram1.hashCode(), ram2.hashCode())
        assertTrue(ram1.toString().contains("100"))

        // ContainerStats
        val container1 =
            ContainerStats(
                "1",
                "test",
                0.1,
                100L,
                200L,
                "running",
                10L,
                20L,
                30L,
                40L,
                5L,
                true,
                500L,
            )
        val container2 = container1.copy()
        val container3 = container1.copy(networkRxBytes = 999L)

        // Equality
        assertEquals(container1, container2)
        assertEquals(container1.hashCode(), container2.hashCode())

        // toString
        val str = container1.toString()
        assertTrue(str.contains("test"))
        assertTrue(str.contains("networkRxBytes=10"))
        assertTrue(str.contains("cpuThrottled=true"))

        // Inequality (branch coverage for equals)
        assertNotEquals(container1, container3)
        // Explicitly check equals(null) and equals(otherType) to avoid generic type inference issues
        // and ensure the specific branches in the generated equals() method are hit.
        assertTrue(container1 != null) // Sanity check, but for coverage:
        val equalsNull = container1.equals(null)
        val equalsString = container1.equals("SomeString")
        assertEquals(false, equalsNull)
        assertEquals(false, equalsString)

        // Component functions (Coverage for componentN)
        assertEquals("1", container1.component1())
        assertEquals("test", container1.component2())
        // ... checking a few is usually enough, but strictly:
        assertEquals(10L, container1.component7()) // networkRxBytes
    }

    @Test
    fun testContainerStatsCopyCoverage() {
        // Explicitly cover copy() for every field to ensure generated code branches are hit
        val base = ContainerStats("id", "name", 0.0, 0, 0, "run")

        assertNotEquals(base, base.copy(id = "id2"))
        assertNotEquals(base, base.copy(name = "name2"))
        assertNotEquals(base, base.copy(cpuPercent = 1.0))
        assertNotEquals(base, base.copy(memoryUsageBytes = 1))
        assertNotEquals(base, base.copy(memoryLimitBytes = 1))
        assertNotEquals(base, base.copy(state = "exit"))
        assertNotEquals(base, base.copy(networkRxBytes = 1))
        assertNotEquals(base, base.copy(networkTxBytes = 1))
        assertNotEquals(base, base.copy(blockReadBytes = 1))
        assertNotEquals(base, base.copy(blockWriteBytes = 1))
        assertNotEquals(base, base.copy(pids = 1))
        assertNotEquals(base, base.copy(cpuThrottled = true))
        assertNotEquals(base, base.copy(memoryMaxUsageBytes = 1))
    }

    @Test
    fun testContainerStatsDefaults() {
        // Test constructor with default arguments to hit those branches
        val container =
            ContainerStats(
                id = "default-test",
                name = "defaults",
                cpuPercent = 0.0,
                memoryUsageBytes = 0,
                memoryLimitBytes = 0,
                state = "created",
            )

        assertEquals(0L, container.networkRxBytes)
        assertEquals(0L, container.networkTxBytes)
        assertEquals(0L, container.blockReadBytes)
        assertEquals(0L, container.blockWriteBytes)
        assertEquals(0L, container.pids)
        assertEquals(false, container.cpuThrottled)
        assertEquals(0L, container.memoryMaxUsageBytes)
    }

    @Test
    fun testContainerStatsDeserializationDefaults() {
        // Json missing the optional fields
        val json =
            """
            {
                "id": "123",
                "name": "nginx",
                "cpuPercent": 0.5,
                "memoryUsageBytes": 1024,
                "memoryLimitBytes": 2048,
                "state": "running"
            }
            """.trimIndent()

        val decoded = Json.decodeFromString<ContainerStats>(json)

        assertEquals(0L, decoded.networkRxBytes)
        assertEquals(0L, decoded.networkTxBytes)
        assertEquals(0L, decoded.blockReadBytes)
        assertEquals(0L, decoded.blockWriteBytes)
        assertEquals(0L, decoded.pids)
        assertEquals(false, decoded.cpuThrottled)
        assertEquals(0L, decoded.memoryMaxUsageBytes)
    }

    @Test
    fun testFullSystemSnapshotMethods() {
        val cpu = CpuStats("M1", 8, 8, 10.0, 30.0)
        val ram = RamStats(100L, 50L, 50L, 50.0)
        val container = ContainerStats("1", "test", 0.1, 100L, 200L, "running")
        val snapshot1 = FullSystemSnapshot(100L, cpu, ram, listOf(container))

        // Copy
        val snapshot2 = snapshot1.copy()
        val snapshot3 = snapshot1.copy(timestamp = 200L)
        val snapshot4 = snapshot1.copy(containers = emptyList())

        assertEquals(snapshot1, snapshot2)
        assertEquals(snapshot1.hashCode(), snapshot2.hashCode())
        assertNotEquals(snapshot1, snapshot3)
        assertNotEquals(snapshot1, snapshot4)

        // toString
        assertTrue(snapshot1.toString().contains("100"))

        // Components
        assertEquals(100L, snapshot1.component1())
        assertEquals(cpu, snapshot1.component2())
        assertEquals(ram, snapshot1.component3())
        assertEquals(listOf(container), snapshot1.component4())

        // Equals edge cases
        assertTrue(snapshot1 != null)
        assertFalse(snapshot1.equals("String"))
    }
}
