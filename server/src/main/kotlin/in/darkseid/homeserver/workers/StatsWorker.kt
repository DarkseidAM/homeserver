package `in`.darkseid.homeserver.workers

import `in`.darkseid.homeserver.data.repository.DockerStatsRepository
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

/**
 * Worker responsible for gathering system statistics and broadcasting them.
 *
 * This worker collects CPU, RAM, and Docker container statistics periodically.
 * It emits updates for real-time monitoring and persists snapshots for historical analysis.
 *
 * @property oshiRepo Repository for OSHI-based system statistics (CPU, RAM).
 * @property dockerRepo Repository for Docker container statistics.
 * @property historyRepo Repository for persisting historical data.
 * @property sqliteDataFlushRate The interval in seconds at which data is saved to the SQLite database. Defaults to 15 seconds.
 */
class StatsWorker(
    oshiRepo: StatsRepository,
    dockerRepo: DockerStatsRepository,
    historyRepo: SqliteHistoryRepository,
    private val sqliteDataFlushRate: Int = 15
) : KoinComponent {
    private val oshiStatsRepository: StatsRepository = oshiRepo
    private val dockerStatsRepository: DockerStatsRepository = dockerRepo
    private val historyRepository: SqliteHistoryRepository = historyRepo

    /**
     * A shared flow that emits the latest [FullSystemSnapshot].
     *
     * Subscribers can collect from this flow to receive real-time updates of system statistics.
     * It replays the latest emission to new subscribers.
     */
    val statsFlow = MutableSharedFlow<FullSystemSnapshot>(replay = 1)

    /**
     * Starts the worker to periodically gather and process statistics.
     *
     * This method launches a coroutine on [Dispatchers.IO] that runs indefinitely.
     * - Every 1 second: Gathers stats and emits a [FullSystemSnapshot] to [statsFlow].
     * - Every [sqliteDataFlushRate] seconds: Saves the snapshot to the [historyRepository].
     * - Every hour (3600 seconds): Prunes old data from the [historyRepository].
     *
     * @param scope The [CoroutineScope] in which the worker coroutine will be launched.
     */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            var ticks = 0
            while (isActive) {
                val now = System.currentTimeMillis()

                // 1. Gather Data
                val cpu = oshiStatsRepository.getCpuStats()
                val ram = oshiStatsRepository.getRamStats()
                val containers = dockerStatsRepository.getLatestStats()

                val snapshot = FullSystemSnapshot(now, cpu, ram, containers)

                // 2. Emit to WebSockets (Every 1s)
                statsFlow.emit(snapshot)

                // 3. Save to DB (Every [sqliteDataFlushRate]s)
                if (ticks % sqliteDataFlushRate == 0) {
                    historyRepository.saveSnapshot(snapshot)
                    // Occasionally prune (Every hour = 3600 ticks)
                    if (ticks % 3600 == 0) historyRepository.pruneOldData()
                }

                ticks++
                delay(1000)
            }
        }
    }
}
