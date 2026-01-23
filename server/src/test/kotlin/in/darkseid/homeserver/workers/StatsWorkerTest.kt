package `in`.darkseid.homeserver.workers

import `in`.darkseid.homeserver.data.repository.DockerStatsRepository
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.ContainerStats
import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StatsWorkerTest {
    @Test
    fun `worker emits stats every second`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val oshiRepo = mockk<StatsRepository>()
            val dockerRepo = mockk<DockerStatsRepository>()
            val historyRepo = mockk<SqliteHistoryRepository>(relaxed = true)

            coEvery { oshiRepo.getCpuStats() } returns CpuStats("M1", 8, 8, 10.0, 40.0)
            coEvery { oshiRepo.getRamStats() } returns RamStats(100L, 50L, 50L, 50.0)
            every { dockerRepo.getLatestStats() } returns
                listOf(
                    ContainerStats(
                        "1",
                        "test",
                        0.0,
                        0,
                        0,
                        "running",
                    ),
                )

            val worker =
                StatsWorker(
                    oshiRepo = oshiRepo,
                    dockerRepo = dockerRepo,
                    historyRepo = historyRepo,
                    sqliteDataFlushRate = 15,
                    dispatcher = testDispatcher,
                )

            val job =
                launch(testDispatcher) {
                    worker.start(this)
                }

            // Advance time by 1.1 seconds (should emit at least once)
            advanceTimeBy(1100)

            val stats = worker.statsFlow.first()
            assertEquals("M1", stats.cpu.model)
            assertEquals(1, stats.containers.size)

            // Verify gathering called
            coVerify { oshiRepo.getCpuStats() }
            verify(atLeast = 1) { dockerRepo.getLatestStats() }

            job.cancel()
        }

    @Test
    fun `worker saves snapshot every flush interval`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val oshiRepo = mockk<StatsRepository>(relaxed = true)
            val dockerRepo = mockk<DockerStatsRepository>(relaxed = true)
            val historyRepo = mockk<SqliteHistoryRepository>(relaxed = true)
            val flushRate = 5 // 5 seconds

            val worker =
                StatsWorker(
                    oshiRepo = oshiRepo,
                    dockerRepo = dockerRepo,
                    historyRepo = historyRepo,
                    sqliteDataFlushRate = flushRate,
                    dispatcher = testDispatcher,
                )

            val job =
                launch(testDispatcher) {
                    worker.start(this)
                }

            // Initial run at 0s: Save? The code says: if (ticks % flushRate == 0)
            // ticks starts at 0. So it saves at 0s.
            advanceTimeBy(100)
            verify(exactly = 1) { historyRepo.saveSnapshots(any()) }

            // Advance 4 more seconds -> 4s total. Next save is at 5s (tick 5).
            // Wait, loop runs every 1s.
            // tick 0: save
            // tick 1: no save
            // tick 2: no save
            // tick 3: no save
            // tick 4: no save
            // tick 5: save

            advanceTimeBy(5000) // Advance 5s. Total 5.1s

            // Should have saved twice (at 0 and 5)
            verify(exactly = 2) { historyRepo.saveSnapshots(any()) }

            job.cancel()
        }

    @Test
    fun `worker prunes data every hour`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val oshiRepo = mockk<StatsRepository>(relaxed = true)
            val dockerRepo = mockk<DockerStatsRepository>(relaxed = true)
            val historyRepo = mockk<SqliteHistoryRepository>(relaxed = true)

            val worker =
                StatsWorker(
                    oshiRepo = oshiRepo,
                    dockerRepo = dockerRepo,
                    historyRepo = historyRepo,
                    sqliteDataFlushRate = 60,
                    dispatcher = testDispatcher,
                )

            val job =
                launch(testDispatcher) {
                    worker.start(this)
                }

            // tick 0: prune (0 % 3600 == 0)
            advanceTimeBy(100)
            verify(exactly = 1) { historyRepo.pruneOldData() }

            // Advance 1 hour (3600 seconds)
            advanceTimeBy(3600 * 1000L)

            // tick 3600: prune

            verify(exactly = 2) { historyRepo.pruneOldData() }

            job.cancel()
        }

    @Test
    fun `worker stops when scope is cancelled`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)

            val oshiRepo = mockk<StatsRepository>(relaxed = true)

            val dockerRepo = mockk<DockerStatsRepository>(relaxed = true)

            val historyRepo = mockk<SqliteHistoryRepository>(relaxed = true)

            val worker =
                StatsWorker(
                    oshiRepo = oshiRepo,
                    dockerRepo = dockerRepo,
                    historyRepo = historyRepo,
                    sqliteDataFlushRate = 1, // Flush every second
                    dispatcher = testDispatcher,
                )

            val job =
                launch(testDispatcher) {
                    worker.start(this)
                }

            // Run for a bit

            advanceTimeBy(2000)

            coVerify(atLeast = 2) { oshiRepo.getCpuStats() }

            // Clear recordings to verify no more calls happen

            io.mockk.clearMocks(
                oshiRepo,
                dockerRepo,
                historyRepo,
                answers = false,
                recordedCalls = true,
            )

            // Cancel the job (stops the while(isActive) loop)

            job.cancel()

            // Advance time significantly

            advanceTimeBy(5000)

            // Verify NO calls happened after cancellation

            coVerify(exactly = 0) { oshiRepo.getCpuStats() }

            verify(exactly = 0) { historyRepo.saveSnapshots(any()) }
        }
}
