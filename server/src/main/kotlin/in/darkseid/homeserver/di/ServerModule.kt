package `in`.darkseid.homeserver.di

import `in`.darkseid.homeserver.data.docker.DockerClientFactory
import `in`.darkseid.homeserver.data.repository.DockerStatsRepository
import `in`.darkseid.homeserver.data.repository.OshiStatsRepository
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.workers.StatsWorker
import org.koin.dsl.module

val serverModule = module {
    single<StatsRepository> { OshiStatsRepository(get()) }
    single { DockerClientFactory.create() }
    single { DockerStatsRepository(get()) }
    single { SqliteHistoryRepository(get<AppConfig>().database.path).apply { init() } }
    single {
        StatsWorker(
            get(),
            get(),
            get(),
            get<AppConfig>().database.flushRate
        )
    }
}
