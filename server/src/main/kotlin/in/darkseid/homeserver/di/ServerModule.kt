package `in`.darkseid.homeserver.di

import `in`.darkseid.homeserver.data.docker.DockerClientFactory
import `in`.darkseid.homeserver.data.repository.DockerStatsRepository
import `in`.darkseid.homeserver.data.repository.OshiStatsRepository
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.workers.StatsWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val serverModule =
    module {
        single<CoroutineDispatcher> { Dispatchers.IO }
        single<StatsRepository> { OshiStatsRepository(get(), get()) }
        single { DockerClientFactory.create() }
        single { DockerStatsRepository(get(), get()) }
        single { SqliteHistoryRepository(get<AppConfig>().database.path) }
        single {
            StatsWorker(
                get(),
                get(),
                get(),
                get<AppConfig>().database.flushRate,
                get(),
            )
        }
    }
