package `in`.darkseid.homeserver.di

import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.models.DatabaseConfig
import `in`.darkseid.homeserver.domain.models.PublishingConfig
import `in`.darkseid.homeserver.domain.models.ServerConfig
import `in`.darkseid.homeserver.workers.StatsWorker
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@KoinExperimentalAPI
class ModuleTest : KoinTest {
    private val testConfigModule =
        module {
            single {
                AppConfig(
                    server = ServerConfig(8080, "localhost"),
                    database = DatabaseConfig("test-check-modules.db", 10, 60),
                    publishing = PublishingConfig(1000, 1000),
                )
            }
        }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun verifyModules() {
        module {
            includes(testConfigModule, serverModule, oshiModule)
        }.verify(
            extraTypes =
                listOf(
                    ServerConfig::class,
                    DatabaseConfig::class,
                    PublishingConfig::class,
                ),
        )
    }

    @Test
    fun resolveComponents() {
        // This test starts Koin with the REAL configModule to ensure it can load the config (from test resources)
        // and resolves StatsWorker to ensure the ServerModule definition block is executed.
        val koin =
            koinApplication {
                modules(configModule, serverModule, oshiModule)
            }.koin

        val appConfig = koin.get<AppConfig>()
        assertNotNull(appConfig)

        val statsWorker = koin.get<StatsWorker>()
        assertNotNull(statsWorker)
    }
}
