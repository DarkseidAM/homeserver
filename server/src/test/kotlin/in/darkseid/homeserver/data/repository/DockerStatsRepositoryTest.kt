package `in`.darkseid.homeserver.data.repository

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.ListContainersCmd
import com.github.dockerjava.api.command.StatsCmd
import com.github.dockerjava.api.model.BlkioStatEntry
import com.github.dockerjava.api.model.BlkioStatsConfig
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.CpuStatsConfig
import com.github.dockerjava.api.model.CpuUsageConfig
import com.github.dockerjava.api.model.MemoryStatsConfig
import com.github.dockerjava.api.model.PidsStatsConfig
import com.github.dockerjava.api.model.StatisticNetworksConfig
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.api.model.ThrottlingDataConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerStatsRepositoryTest {
    @Test
    fun `getLatestStats starts monitoring new containers and returns stats`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"
        val containerName = "/test-container"

        // Mock ListContainersCmd
        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf(containerName)

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        // Mock StatsCmd
        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()

        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        // 1. First call to getLatestStats - should start monitoring
        var stats = repository.getLatestStats()
        assertTrue(stats.isEmpty(), "Initially empty as callback hasn't fired")

        // 2. Trigger callback with stats
        val statistics = createMockStatistics()
        callbackSlot.captured.onNext(statistics)

        // 3. Second call - should return the stats stored in liveStats
        stats = repository.getLatestStats()

        assertEquals(1, stats.size)
        val containerStats = stats.first()
        assertEquals(containerId, containerStats.id)
        assertEquals("test-container", containerStats.name)
        assertTrue(containerStats.cpuPercent > 0.0)
        assertEquals(1024L, containerStats.memoryUsageBytes)

        // Assert New Fields
        assertEquals(300L, containerStats.networkRxBytes) // 100 + 200
        assertEquals(600L, containerStats.networkTxBytes) // 200 + 400
        assertEquals(150L, containerStats.blockReadBytes) // 50 + 100
        assertEquals(300L, containerStats.blockWriteBytes) // 100 + 200
        assertEquals(123L, containerStats.pids)
        assertTrue(containerStats.cpuThrottled)
        assertEquals(2048L, containerStats.memoryMaxUsageBytes)

        verify(exactly = 1) { statsCmd.exec(any()) }
    }

    @Test
    fun `getLatestStats stops monitoring removed containers`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        // Setup: Container running
        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(any()) } answers { firstArg() }

        repository.getLatestStats() // Start monitoring

        // Act: Container stopped (no longer in list)
        every { listContainersCmd.exec() } returns emptyList()

        val stats = repository.getLatestStats()

        // Assert
        assertTrue(stats.isEmpty(), "Stats should be empty after container stops")
    }

    @Test
    fun `close clears active streams`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        // Setup: Container running
        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(any()) } answers { firstArg() }

        // 1. Start monitoring
        repository.getLatestStats()

        // 2. Close repository (should clear activeStreams)
        repository.close()

        // 3. Call getLatestStats again
        // Since activeStreams is empty but container is still running (mocked),
        // it should trigger startMonitoring (statsCmd.exec) again.
        repository.getLatestStats()

        // Assert: exec should be called twice (initial + after restart)
        verify(exactly = 2) { statsCmd.exec(any()) }
    }

    @Test
    fun `onNext ignores statistics with missing data`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats() // Start monitoring

        // Case 1: cpuStats is null
        val statsNoCpu = mockk<Statistics>(relaxed = true)
        every { statsNoCpu.cpuStats } returns null
        callbackSlot.captured.onNext(statsNoCpu)
        assertTrue(repository.getLatestStats().isEmpty(), "Should not update with null cpuStats")

        // Case 2: preCpuStats is null
        val statsNoPreCpu = mockk<Statistics>(relaxed = true)
        every { statsNoPreCpu.cpuStats } returns mockk(relaxed = true)
        every { statsNoPreCpu.preCpuStats } returns null
        callbackSlot.captured.onNext(statsNoPreCpu)
        assertTrue(repository.getLatestStats().isEmpty(), "Should not update with null preCpuStats")

        // Case 3: cpuUsage totalUsage is null
        val statsNoTotalUsage = mockk<Statistics>(relaxed = true)
        val cpuStats = mockk<CpuStatsConfig>(relaxed = true)
        val cpuUsage = mockk<CpuUsageConfig>(relaxed = true)

        every { cpuUsage.totalUsage } returns null
        every { cpuStats.cpuUsage } returns cpuUsage
        every { statsNoTotalUsage.cpuStats } returns cpuStats
        every { statsNoTotalUsage.preCpuStats } returns mockk(relaxed = true)

        callbackSlot.captured.onNext(statsNoTotalUsage)
        assertTrue(repository.getLatestStats().isEmpty(), "Should not update with null totalUsage")
    }

    @Test
    fun `onNext calculates zero cpu percent when deltas are non-positive`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats() // Start monitoring

        // Case 1: systemDelta <= 0 (systemUsage == preSystemUsage)
        // Set both systemUsage and preSystemUsage to 5000L
        val statsZeroSystemDelta =
            createMockStatistics(
                systemUsage = 5000L,
                preSystemUsage = 5000L,
            )

        callbackSlot.captured.onNext(statsZeroSystemDelta)
        var stats = repository.getLatestStats()
        assertEquals(0.0, stats.first().cpuPercent, "CPU percent should be 0.0 when systemDelta is 0")

        // Case 2: cpuDelta <= 0 (totalUsage == preTotalUsage)
        // Set both totalUsage and preTotalUsage to 2000L.
        // Defaults for systemUsage (5000) and preSystemUsage (4000) ensure systemDelta > 0.
        val statsZeroCpuDelta =
            createMockStatistics(
                totalUsage = 2000L,
                preTotalUsage = 2000L,
            )

        callbackSlot.captured.onNext(statsZeroCpuDelta)
        stats = repository.getLatestStats()
        assertEquals(0.0, stats.first().cpuPercent, "CPU percent should be 0.0 when cpuDelta is 0")
    }

    @Test
    fun `onNext handles null values in optional fields`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats() // Start monitoring

        // Create stats with valid CPU/Memory (to pass the main if-check) but NULL optional fields
        val stats =
            createMockStatistics(
                totalUsage = 2000L,
                preTotalUsage = 1000L,
                systemUsage = 5000L,
                preSystemUsage = 4000L,
            )

        // Force optional fields to null to test safe calls (?.) and elvis operators (?:)
        every { stats.networks } returns null
        every { stats.blkioStats } returns null
        every { stats.pidsStats } returns null

        // Mock cpuStats but with null throttlingData
        val cpuStats = stats.cpuStats!!
        every { cpuStats.throttlingData } returns null

        // Mock memoryStats but with null maxUsage
        val memoryStats = stats.memoryStats!!
        every { memoryStats.maxUsage } returns null

        callbackSlot.captured.onNext(stats)

        val result = repository.getLatestStats().first()

        assertEquals(0L, result.networkRxBytes)
        assertEquals(0L, result.networkTxBytes)
        assertEquals(0L, result.blockReadBytes)
        assertEquals(0L, result.blockWriteBytes)
        assertEquals(0L, result.pids)
        assertEquals(false, result.cpuThrottled)
        assertEquals(0L, result.memoryMaxUsageBytes)
    }

    @Test
    fun `onNext handles partial nulls in nested fields`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats()

        val stats = createMockStatistics()

        // 1. Network with null bytes
        val netConfig = mockk<StatisticNetworksConfig>()
        every { netConfig.rxBytes } returns null
        every { netConfig.txBytes } returns null
        every { stats.networks } returns mapOf("eth0" to netConfig)

        // 2. Blkio with null op and value
        val blkEntryNulls = mockk<BlkioStatEntry>()
        every { blkEntryNulls.op } returns null // Should be handled gracefully
        every { blkEntryNulls.value } returns null

        val blkConfig = mockk<BlkioStatsConfig>()
        every { blkConfig.ioServiceBytesRecursive } returns listOf(blkEntryNulls)
        every { stats.blkioStats } returns blkConfig

        callbackSlot.captured.onNext(stats)

        val result = repository.getLatestStats().first()

        assertEquals(0L, result.networkRxBytes)
        assertEquals(0L, result.networkTxBytes)
        assertEquals(0L, result.blockReadBytes)
        assertEquals(0L, result.blockWriteBytes)
    }

    @Test
    fun `getLatestStats handles container with no name`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id-no-name"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns emptyArray() // Force firstOrNull() to be null

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(any()) } answers { firstArg() }

        // Start monitoring
        val stats = repository.getLatestStats()
        // Wait... getLatestStats() returns values from liveStats map.
        // Initially it's empty until callback fires.
        // But the key point is verifying the "unknown" name logic passed to startMonitoring.

        // We can capture the callback and trigger it to see what name is stored in liveStats
        val callbackSlot = slot<ResultCallback<Statistics>>()
        verify { statsCmd.exec(capture(callbackSlot)) }

        callbackSlot.captured.onNext(createMockStatistics())

        val result = repository.getLatestStats().first()
        assertEquals("unknown", result.name)
    }

    @Test
    fun `onNext handles null stats`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats()

        // Pass null to onNext
        callbackSlot.captured.onNext(null)

        assertTrue(repository.getLatestStats().isEmpty(), "Should ignore null stats")
    }

    @Test
    fun `onNext handles empty collections and intermediate nulls`() {
        val dockerClient = mockk<DockerClient>()
        val repository = DockerStatsRepository(dockerClient)
        val containerId = "test-container-id"

        val listContainersCmd = mockk<ListContainersCmd>()
        val container = mockk<Container>()
        every { container.id } returns containerId
        every { container.names } returns arrayOf("/test-container")

        every { dockerClient.listContainersCmd() } returns listContainersCmd
        every { listContainersCmd.withStatusFilter(any()) } returns listContainersCmd
        every { listContainersCmd.exec() } returns listOf(container)

        val statsCmd = mockk<StatsCmd>()
        val callbackSlot = slot<ResultCallback<Statistics>>()
        every { dockerClient.statsCmd(containerId) } returns statsCmd
        every { statsCmd.exec(capture(callbackSlot)) } answers { callbackSlot.captured }

        repository.getLatestStats()

        val stats = createMockStatistics()

        // 1. Empty Collections
        every { stats.networks } returns emptyMap()

        val blkConfig = mockk<BlkioStatsConfig>()
        every { blkConfig.ioServiceBytesRecursive } returns emptyList()
        every { stats.blkioStats } returns blkConfig

        // 2. Intermediate Nulls (PIDs, Throttling)
        // We keep CPU stats VALID so we enter the if-block

        // pidsStats present, but current null
        val pidsStats = mockk<PidsStatsConfig>()
        every { pidsStats.current } returns null
        every { stats.pidsStats } returns pidsStats

        // throttlingData present, but throttledPeriods null
        val cpuStats = stats.cpuStats!! // Use existing valid mock
        val throttlingData = mockk<ThrottlingDataConfig>()
        every { throttlingData.throttledPeriods } returns null
        every { cpuStats.throttlingData } returns throttlingData

        callbackSlot.captured.onNext(stats)

        val result = repository.getLatestStats().first()

        assertEquals(0L, result.networkRxBytes)
        assertEquals(0L, result.blockReadBytes)
        assertEquals(0L, result.pids)
        assertEquals(false, result.cpuThrottled)
    }

    private fun createMockStatistics(
        totalUsage: Long = 2000L,
        preTotalUsage: Long = 1000L,
        systemUsage: Long = 5000L,
        preSystemUsage: Long = 4000L,
    ): Statistics {
        val stats = mockk<Statistics>()
        val cpuStats = mockk<CpuStatsConfig>()
        val preCpuStats = mockk<CpuStatsConfig>()
        val memoryStats = mockk<MemoryStatsConfig>()
        val blkioStats = mockk<BlkioStatsConfig>()
        val pidsStats = mockk<PidsStatsConfig>()

        val cpuUsage = mockk<CpuUsageConfig>()
        every { cpuUsage.totalUsage } returns totalUsage
        every { cpuStats.cpuUsage } returns cpuUsage
        every { cpuStats.systemCpuUsage } returns systemUsage
        every { cpuStats.onlineCpus } returns 4

        // Throttling
        val throttlingData = mockk<ThrottlingDataConfig>()
        every { throttlingData.throttledPeriods } returns 1L
        every { cpuStats.throttlingData } returns throttlingData

        val preCpuUsage = mockk<CpuUsageConfig>()
        every { preCpuUsage.totalUsage } returns preTotalUsage
        every { preCpuStats.cpuUsage } returns preCpuUsage
        every { preCpuStats.systemCpuUsage } returns preSystemUsage

        every { stats.cpuStats } returns cpuStats
        every { stats.preCpuStats } returns preCpuStats

        every { memoryStats.usage } returns 1024L
        every { memoryStats.limit } returns 2048L
        every { memoryStats.maxUsage } returns 2048L
        every { stats.memoryStats } returns memoryStats

        // Network
        val net1 = mockk<StatisticNetworksConfig>()
        every { net1.rxBytes } returns 100L
        every { net1.txBytes } returns 200L
        val net2 = mockk<StatisticNetworksConfig>()
        every { net2.rxBytes } returns 200L
        every { net2.txBytes } returns 400L
        every { stats.networks } returns mapOf("eth0" to net1, "eth1" to net2)

        // Block I/O
        val blkRead1 = mockk<BlkioStatEntry>()
        every { blkRead1.op } returns "Read"
        every { blkRead1.value } returns 50L
        val blkRead2 = mockk<BlkioStatEntry>()
        every { blkRead2.op } returns "read" // lowercase check
        every { blkRead2.value } returns 100L
        val blkWrite1 = mockk<BlkioStatEntry>()
        every { blkWrite1.op } returns "Write"
        every { blkWrite1.value } returns 100L
        val blkWrite2 = mockk<BlkioStatEntry>()
        every { blkWrite2.op } returns "WRITE" // Mixed case check
        every { blkWrite2.value } returns 200L
        val blkSync = mockk<BlkioStatEntry>()
        every { blkSync.op } returns "Sync"
        every { blkSync.value } returns 9999L

        every { blkioStats.ioServiceBytesRecursive } returns listOf(blkRead1, blkRead2, blkWrite1, blkWrite2, blkSync)
        every { stats.blkioStats } returns blkioStats

        // PIDs
        every { pidsStats.current } returns 123L
        every { stats.pidsStats } returns pidsStats

        return stats
    }
}
