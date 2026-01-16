package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AppConfigTest {
    @Test
    fun testMissingRequiredField() {
        // host is missing, should fail
        val invalidJsonNoHost = """{"port": 8080}"""
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ServerConfig>(invalidJsonNoHost)
        }

        // port is missing, should fail
        val invalidJsonNoPort = """{"host": "localhost"}"""
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ServerConfig>(invalidJsonNoPort)
        }
    }

    @Test
    fun testSerializers() {
        assertNotNull(AppConfig.serializer())
        assertNotNull(ServerConfig.serializer())
        assertNotNull(DatabaseConfig.serializer())
        assertNotNull(PublishingConfig.serializer())

        assertEquals("in.darkseid.homeserver.domain.models.AppConfig", AppConfig.serializer().descriptor.serialName)
    }

    @Test
    fun testSerialization() {
        val config =
            AppConfig(
                server = ServerConfig(port = 8080, host = "0.0.0.0"),
                database = DatabaseConfig(path = "./db", maxPoolSize = 10, flushRate = 1000),
                publishing = PublishingConfig(cpuStatsFrequency = 500, ramStatsFrequency = 1000),
            )

        val json = Json.encodeToString(config)
        val decoded = Json.decodeFromString<AppConfig>(json)

        assertEquals(config, decoded)

        // Verify getters
        assertEquals(8080, config.server.port)
        assertEquals("0.0.0.0", config.server.host)
        assertEquals("./db", config.database.path)
        assertEquals(10, config.database.maxPoolSize)
        assertEquals(1000, config.database.flushRate)
        assertEquals(500, config.publishing.cpuStatsFrequency)
        assertEquals(1000, config.publishing.ramStatsFrequency)
    }
}
