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
        assertNotNull(StorageStats.serializer())
        assertNotNull(NetworkStats.serializer())
        assertNotNull(SystemStats.serializer())

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
        val storage = StorageStats(emptyList(), emptyList())
        val network = NetworkStats(emptyList())
        val system =
            SystemStats(
                "Linux",
                "Canonical",
                "22.04",
                "Dell",
                "XPS",
                "Intel",
                1000L,
                100,
                10,
                1.2,
                emptyList(),
            )

        val snapshot =
            FullSystemSnapshot(
                timestamp = 123456789L,
                cpu = cpu,
                ram = ram,
                containers = listOf(container),
                storage = storage,
                network = network,
                system = system,
            )

        val json = Json.encodeToString(snapshot)
        val decoded = Json.decodeFromString<FullSystemSnapshot>(json)
        assertEquals(snapshot, decoded)

        // Verify getters for FullSystemSnapshot and children
        assertEquals(123456789L, snapshot.timestamp)
        assertEquals(cpu, snapshot.cpu)
        assertEquals(ram, snapshot.ram)
        assertEquals(listOf(container), snapshot.containers)
        assertEquals(storage, snapshot.storage)
        assertEquals(network, snapshot.network)
        assertEquals(system, snapshot.system)
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
    fun testStorageStatsCoverage() {
        val partition =
            PartitionStat(
                mount = "/",
                name = "root",
                type = "ext4",
                totalSpace = 1000L,
                usedSpace = 500L,
                availableSpace = 500L,
                usagePercent = 50.0,
            )
        val disk =
            DiskDeviceStat(
                name = "sda",
                model = "SSD",
                size = 1000L,
                readBytes = 100L,
                writeBytes = 200L,
                transferTime = 10L,
            )
        val storage = StorageStats(listOf(partition), listOf(disk))

        // PartitionStat Coverage
        val p2 = partition.copy()
        val p3 = partition.copy(mount = "/home")
        assertEquals(partition, p2)
        assertNotEquals(partition, p3)
        assertEquals(partition.hashCode(), p2.hashCode())
        assertTrue(partition.toString().contains("root"))
        assertEquals("/", partition.component1())
        assertEquals("root", partition.component2())
        assertEquals("ext4", partition.component3())
        assertEquals(1000L, partition.component4())
        assertEquals(500L, partition.component5())
        assertEquals(500L, partition.component6())
        assertEquals(50.0, partition.component7())

        // DiskDeviceStat Coverage
        val d2 = disk.copy()
        val d3 = disk.copy(name = "sdb")
        assertEquals(disk, d2)
        assertNotEquals(disk, d3)
        assertEquals(disk.hashCode(), d2.hashCode())
        assertTrue(disk.toString().contains("SSD"))
        assertEquals("sda", disk.component1())
        assertEquals("SSD", disk.component2())
        assertEquals(1000L, disk.component3())
        assertEquals(100L, disk.component4())
        assertEquals(200L, disk.component5())
        assertEquals(10L, disk.component6())

        // StorageStats Coverage
        val s2 = storage.copy()
        val s3 = storage.copy(partitions = emptyList())
        assertEquals(storage, s2)
        assertNotEquals(storage, s3)
        assertEquals(storage.hashCode(), s2.hashCode())
        assertTrue(storage.toString().contains("root"))
        assertEquals(listOf(partition), storage.component1())
        assertEquals(listOf(disk), storage.component2())
    }

    @Test
    fun testNetworkStatsCoverage() {
        val iface =
            InterfaceStat(
                name = "eth0",
                displayName = "Ethernet",
                macAddress = "00:00:00:00:00:00",
                ipv4 = listOf("192.168.1.1"),
                ipv6 = listOf("::1"),
                bytesSent = 100L,
                bytesRecv = 200L,
                packetsSent = 10L,
                packetsRecv = 20L,
                speed = 1000L,
                isUp = true,
            )
        val network = NetworkStats(listOf(iface))

        // InterfaceStat Coverage
        val i2 = iface.copy()
        val i3 = iface.copy(name = "wlan0")
        assertEquals(iface, i2)
        assertNotEquals(iface, i3)
        assertEquals(iface.hashCode(), i2.hashCode())
        assertTrue(iface.toString().contains("eth0"))
        assertEquals("eth0", iface.component1())
        assertEquals("Ethernet", iface.component2())
        assertEquals("00:00:00:00:00:00", iface.component3())
        assertEquals(listOf("192.168.1.1"), iface.component4())
        assertEquals(listOf("::1"), iface.component5())
        assertEquals(100L, iface.component6())
        assertEquals(200L, iface.component7())
        assertEquals(10L, iface.component8())
        assertEquals(20L, iface.component9())
        assertEquals(1000L, iface.component10())
        assertEquals(true, iface.component11())

        // NetworkStats Coverage
        val n2 = network.copy()
        val n3 = network.copy(interfaces = emptyList())
        assertEquals(network, n2)
        assertNotEquals(network, n3)
        assertEquals(network.hashCode(), n2.hashCode())
        assertTrue(network.toString().contains("eth0"))
        assertEquals(listOf(iface), network.component1())
    }

    @Test
    fun testSystemStatsCoverage() {
        val sys =
            SystemStats(
                osFamily = "Linux",
                osManufacturer = "Canonical",
                osVersion = "22.04",
                systemManufacturer = "Dell",
                systemModel = "XPS",
                processorName = "Intel",
                uptime = 1000L,
                processCount = 100,
                threadCount = 200,
                cpuVoltage = 1.2,
                fanSpeeds = listOf(1000),
            )

        val s2 = sys.copy()
        val s3 = sys.copy(osFamily = "Windows")

        assertEquals(sys, s2)
        assertNotEquals(sys, s3)
        assertEquals(sys.hashCode(), s2.hashCode())
        assertTrue(sys.toString().contains("Linux"))

        assertEquals("Linux", sys.component1())
        assertEquals("Canonical", sys.component2())
        assertEquals("22.04", sys.component3())
        assertEquals("Dell", sys.component4())
        assertEquals("XPS", sys.component5())
        assertEquals("Intel", sys.component6())
        assertEquals(1000L, sys.component7())
        assertEquals(100, sys.component8())
        assertEquals(200, sys.component9())
        assertEquals(1.2, sys.component10())
        assertEquals(listOf(1000), sys.component11())
    }

    @Test
    fun testFullSystemSnapshotMethods() {
        val cpu = CpuStats("M1", 8, 8, 10.0, 30.0)
        val ram = RamStats(100L, 50L, 50L, 50.0)
        val container = ContainerStats("1", "test", 0.1, 100L, 200L, "running")
        val storage = StorageStats(emptyList(), emptyList())
        val network = NetworkStats(emptyList())
        val system =
            SystemStats(
                "Linux",
                "Canonical",
                "22.04",
                "Dell",
                "XPS",
                "Intel",
                1000L,
                100,
                10,
                1.2,
                emptyList(),
            )

        val snapshot1 = FullSystemSnapshot(100L, cpu, ram, listOf(container), storage, network, system)

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
        assertEquals(storage, snapshot1.component5())
        assertEquals(network, snapshot1.component6())
        assertEquals(system, snapshot1.component7())

        // Equals edge cases
        assertTrue(snapshot1 != null)
        assertFalse(snapshot1.equals("String"))
    }
}
