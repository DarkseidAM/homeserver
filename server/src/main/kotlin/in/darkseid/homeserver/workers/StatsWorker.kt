package `in`.darkseid.homeserver.workers

import `in`.darkseid.homeserver.data.repository.DockerStatsRepository
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.utils.named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val sqliteDataFlushRate: Int = 15,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
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
    private val _statsFlow =
        MutableSharedFlow<FullSystemSnapshot>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val statsFlow: SharedFlow<FullSystemSnapshot> = _statsFlow.asSharedFlow()

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
        scope.launch(dispatcher + named("StatsWorker")) {
            var ticks = 0
            while (true) {
                val now = System.currentTimeMillis()

                // 1. Gather Data
                val cpu = oshiStatsRepository.getCpuStats()
                val ram = oshiStatsRepository.getRamStats()
                val containers = dockerStatsRepository.getLatestStats()

                val snapshot = FullSystemSnapshot(now, cpu, ram, containers)

                // 2. Emit to WebSockets (Every 1s)
                _statsFlow.emit(snapshot)

                // 3. Save to DB (Every [sqliteDataFlushRate]s)
                if (ticks % sqliteDataFlushRate == 0) {
                    historyRepository.saveSnapshot(snapshot)
                    // Occasionally prune (Every hour = 3600 ticks)
                    if (ticks % PRUNE_INTERVAL == 0) historyRepository.pruneOldData()
                }

                ticks++
                delay(WORKER_FREQUENCY)
            }
        }
    }

    companion object {
        private const val PRUNE_INTERVAL = 3600 // 1 hour
        private const val WORKER_FREQUENCY = 1000L // 1 sec
    }
}
