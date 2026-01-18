package `in`.darkseid.homeserver

import com.typesafe.config.ConfigFactory
import `in`.darkseid.homeserver.domain.models.AppConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testConfigLoading() {
        val configStr =
            """
            server {
                port = 8080
                host = "0.0.0.0"
            }
            database {
                path = "test.db"
                maxPoolSize = 10
                flushRate = 5
            }
            publishing {
                cpuStatsFrequency = 1000
                ramStatsFrequency = 1000
            }
            """.trimIndent()

        val rawConfig = ConfigFactory.parseString(configStr)
        val appConfig = Hocon.decodeFromConfig<AppConfig>(rawConfig)

        assertEquals(8080, appConfig.server.port)
        assertEquals("0.0.0.0", appConfig.server.host)
        assertEquals("test.db", appConfig.database.path)
        assertEquals(10, appConfig.database.maxPoolSize)
        assertEquals(5, appConfig.database.flushRate)
        assertEquals(1000L, appConfig.publishing.cpuStatsFrequency)
        assertEquals(1000L, appConfig.publishing.ramStatsFrequency)
    }
}
