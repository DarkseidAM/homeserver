package `in`.darkseid.homeserver.di

import org.koin.dsl.module
import oshi.SystemInfo

val oshiModule = module {
    single<SystemInfo> { SystemInfo() }
}
