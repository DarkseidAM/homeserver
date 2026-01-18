package `in`.darkseid.homeserver.data.repository

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.ListContainersCmd
import com.github.dockerjava.api.command.StatsCmd
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.CpuStatsConfig
import com.github.dockerjava.api.model.CpuUsageConfig
import com.github.dockerjava.api.model.MemoryStatsConfig
import com.github.dockerjava.api.model.Statistics
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

        val cpuUsage = mockk<CpuUsageConfig>()
        every { cpuUsage.totalUsage } returns totalUsage
        every { cpuStats.cpuUsage } returns cpuUsage
        every { cpuStats.systemCpuUsage } returns systemUsage
        every { cpuStats.onlineCpus } returns 4

        val preCpuUsage = mockk<CpuUsageConfig>()
        every { preCpuUsage.totalUsage } returns preTotalUsage
        every { preCpuStats.cpuUsage } returns preCpuUsage
        every { preCpuStats.systemCpuUsage } returns preSystemUsage

        every { stats.cpuStats } returns cpuStats
        every { stats.preCpuStats } returns preCpuStats

        every { memoryStats.usage } returns 1024L
        every { memoryStats.limit } returns 2048L
        every { stats.memoryStats } returns memoryStats

        return stats
    }
}
