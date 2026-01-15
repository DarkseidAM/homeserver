package `in`.darkseid.homeserver.di

import com.typesafe.config.ConfigFactory
import `in`.darkseid.homeserver.domain.models.AppConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import org.koin.dsl.module

val configModule =
    module {
        single<AppConfig> {
            val rawConfig = ConfigFactory.load()

            @OptIn(ExperimentalSerializationApi::class)
            Hocon.decodeFromConfig(rawConfig)
        }
    }
