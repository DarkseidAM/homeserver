package `in`.darkseid.homeserver.di

import `in`.darkseid.homeserver.data.OshiStatsRepository
import `in`.darkseid.homeserver.repository.StatsRepository
import org.koin.dsl.module

val serverModule = module {
    single<StatsRepository> { OshiStatsRepository(get()) }
}
