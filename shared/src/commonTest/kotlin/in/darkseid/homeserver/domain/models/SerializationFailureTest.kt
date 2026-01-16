package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SerializationFailureTest {
    @Test
    fun testAppConfigFailures() {
        // Missing server
        assertFailsWith<SerializationException> {
            Json.decodeFromString<AppConfig>("""{"database": {}, "publishing": {}}""")
        }
    }

    @Test
    fun testCpuStatsFailures() {
        // Missing model
        val json =
            """
            {"physicalCores": 4, "logicalCores": 8, "usagePercent": 0.0, "temperature": 30.0}
            """.trimIndent()
        assertFailsWith<SerializationException> {
            Json.decodeFromString<CpuStats>(json)
        }
    }

    @Test
    fun testRamStatsFailures() {
        // Missing total
        assertFailsWith<SerializationException> {
            Json.decodeFromString<RamStats>("""{"used": 100, "available": 100, "usagePercent": 0.5}""")
        }
    }

    @Test
    fun testContainerStatsFailures() {
        // Missing id
        val json =
            """
            {"name": "test", "cpuPercent": 0.0, "memoryUsageBytes": 0, "memoryLimitBytes": 0, "state": "running"}
            """.trimIndent()
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ContainerStats>(json)
        }
    }
}
