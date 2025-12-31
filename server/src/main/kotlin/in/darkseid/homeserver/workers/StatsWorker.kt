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

class StatsWorker(oshiRepo: StatsRepository, dockerRepo: DockerStatsRepository, historyRepo: SqliteHistoryRepository) :
    KoinComponent {
    private val oshiStatsRepository: StatsRepository = oshiRepo
    private val dockerStatsRepository: DockerStatsRepository = dockerRepo
    private val historyRepository: SqliteHistoryRepository = historyRepo

    val statsFlow = MutableSharedFlow<FullSystemSnapshot>(replay = 1)

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

                // 3. Save to DB (Every 15s)
                if (ticks % 15 == 0) {
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
