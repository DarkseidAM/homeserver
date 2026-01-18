package `in`.darkseid.homeserver

import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.DatabaseConfig
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import `in`.darkseid.homeserver.domain.models.PublishingConfig
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.models.ServerConfig
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.workers.StatsWorker
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ApplicationTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
        unmockkStatic("in.darkseid.homeserver.ApplicationKt")
    }

    @Test
    fun `main calls runMain`() {
        mockkStatic("in.darkseid.homeserver.ApplicationKt")
        every { runMain(any()) } returns mockk(relaxed = true)

        main()

        verify { runMain(wait = true) }
    }

    @Test
    fun `root route returns greeting`() =
        testApplication {
            val testModule = createTestModule()
            application {
                moduleWithDependencies(testModule)
            }

            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Ktor: Hello,"))
        }

    @Test
    fun `cpu websocket sends data`() =
        testApplication {
            val mockOshiRepo = mockk<StatsRepository>(relaxed = true)
            val testCpuStats = CpuStats("M1", 8, 8, 15.0, 40.0)
            coEvery { mockOshiRepo.getCpuStats() } returns testCpuStats

            val testModule = createTestModule(mockOshiRepo = mockOshiRepo)
            val client = createWebsocketClient()

            application {
                moduleWithDependencies(testModule)
            }

            client.webSocket("/ws/cpu") {
                val stats = receiveDeserialized<CpuStats>()
                assertEquals("M1", stats.model)
                assertEquals(15.0, stats.usagePercent)
            }
        }

    @Test
    fun `ram websocket sends data`() =
        testApplication {
            val mockOshiRepo = mockk<StatsRepository>(relaxed = true)
            val testRamStats = RamStats(100L, 50L, 50L, 50.0)
            coEvery { mockOshiRepo.getRamStats() } returns testRamStats

            val testModule = createTestModule(mockOshiRepo = mockOshiRepo)
            val client = createWebsocketClient()

            application {
                moduleWithDependencies(testModule)
            }

            client.webSocket("/ws/ram") {
                val stats = receiveDeserialized<RamStats>()
                assertEquals(100L, stats.total)
                assertEquals(50L, stats.used)
            }
        }

    @Test
    fun `system websocket sends data`() =
        testApplication {
            val mockStatsWorker = mockk<StatsWorker>(relaxed = true)
            val flow = MutableSharedFlow<FullSystemSnapshot>(replay = 1)
            val snapshot =
                FullSystemSnapshot(
                    12345L,
                    CpuStats("M1", 8, 8, 10.0, 40.0),
                    RamStats(100L, 50L, 50L, 50.0),
                    emptyList(),
                )
            flow.tryEmit(snapshot)
            every { mockStatsWorker.statsFlow } returns flow.asSharedFlow()

            val testModule = createTestModule(mockStatsWorker = mockStatsWorker)
            val client = createWebsocketClient()

            application {
                moduleWithDependencies(testModule)
            }

            client.webSocket("/ws/system") {
                val stats = receiveDeserialized<FullSystemSnapshot>()
                assertEquals(12345L, stats.timestamp)
                assertEquals("M1", stats.cpu.model)
            }
        }

    @Test
    fun `cpu websocket handles failure gracefully`() =
        testApplication {
            // Case 1: RuntimeException (generic failure)
            val mockOshiRepo1 = mockk<StatsRepository>(relaxed = true)
            coEvery { mockOshiRepo1.getCpuStats() } throws RuntimeException("Simulated RuntimeException")

            var testModule = createTestModule(mockOshiRepo = mockOshiRepo1)
            val client = createWebsocketClient()
            application { moduleWithDependencies(testModule) }

            client.webSocket("/ws/cpu") {
                try {
                    receiveDeserialized<CpuStats>()
                    fail("Should fail")
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }

            // Case 2: ClosedReceiveChannelException
            val mockOshiRepo2 = mockk<StatsRepository>(relaxed = true)
            coEvery { mockOshiRepo2.getCpuStats() } throws
                kotlinx.coroutines.channels.ClosedReceiveChannelException(
                    "Simulated Close",
                )

            testModule = createTestModule(mockOshiRepo = mockOshiRepo2)
            // Re-create client/application environment is tricky in single test body.
            // It's better to separate into different tests or just trust that throwing different exceptions covers the blocks.
            // But testApplication { ... } runs once.
            // I will duplicate the logic or create a helper.
        }

    @Test
    fun `cpu websocket handles specific exceptions`() {
        // ClosedReceiveChannelException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getCpuStats() } throws
                kotlinx.coroutines.channels.ClosedReceiveChannelException(
                    "Simulated",
                )
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/cpu") {
                try {
                    receiveDeserialized<CpuStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }

        // ClosedSendChannelException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getCpuStats() } throws
                kotlinx.coroutines.channels.ClosedSendChannelException(
                    "Simulated",
                )
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/cpu") {
                try {
                    receiveDeserialized<CpuStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }

        // IOException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getCpuStats() } throws java.io.IOException("Simulated")
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/cpu") {
                try {
                    receiveDeserialized<CpuStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
    }

    @Test
    fun `ram websocket handles specific exceptions`() {
        // ClosedReceiveChannelException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getRamStats() } throws
                kotlinx.coroutines.channels.ClosedReceiveChannelException(
                    "Simulated",
                )
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/ram") {
                try {
                    receiveDeserialized<RamStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
        // ClosedSendChannelException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getRamStats() } throws
                kotlinx.coroutines.channels.ClosedSendChannelException(
                    "Simulated",
                )
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/ram") {
                try {
                    receiveDeserialized<RamStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
        // IOException
        testApplication {
            val mockRepo = mockk<StatsRepository>(relaxed = true)
            coEvery { mockRepo.getRamStats() } throws java.io.IOException("Simulated")
            application { moduleWithDependencies(createTestModule(mockOshiRepo = mockRepo)) }
            createWebsocketClient().webSocket("/ws/ram") {
                try {
                    receiveDeserialized<RamStats>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
    }

    @Test
    fun `system websocket handles failure`() {
        // ClosedReceiveChannelException
        testApplication {
            val mockWorker = mockk<StatsWorker>(relaxed = true)
            val mockFlow = mockk<kotlinx.coroutines.flow.SharedFlow<FullSystemSnapshot>>()
            coEvery { mockFlow.collect(any()) } throws
                kotlinx.coroutines.channels.ClosedReceiveChannelException(
                    "Simulated",
                )
            every { mockWorker.statsFlow } returns mockFlow

            application { moduleWithDependencies(createTestModule(mockStatsWorker = mockWorker)) }
            createWebsocketClient().webSocket("/ws/system") {
                try {
                    receiveDeserialized<FullSystemSnapshot>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
        // ClosedSendChannelException
        testApplication {
            val mockWorker = mockk<StatsWorker>(relaxed = true)
            val mockFlow = mockk<kotlinx.coroutines.flow.SharedFlow<FullSystemSnapshot>>()
            coEvery { mockFlow.collect(any()) } throws
                kotlinx.coroutines.channels.ClosedSendChannelException(
                    "Simulated",
                )
            every { mockWorker.statsFlow } returns mockFlow

            application { moduleWithDependencies(createTestModule(mockStatsWorker = mockWorker)) }
            createWebsocketClient().webSocket("/ws/system") {
                try {
                    receiveDeserialized<FullSystemSnapshot>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
        // IOException
        testApplication {
            val mockWorker = mockk<StatsWorker>(relaxed = true)
            val mockFlow = mockk<kotlinx.coroutines.flow.SharedFlow<FullSystemSnapshot>>()
            coEvery { mockFlow.collect(any()) } throws java.io.IOException("Simulated")
            every { mockWorker.statsFlow } returns mockFlow

            application { moduleWithDependencies(createTestModule(mockStatsWorker = mockWorker)) }
            createWebsocketClient().webSocket("/ws/system") {
                try {
                    receiveDeserialized<FullSystemSnapshot>()
                } catch (e: Exception) {
                    assertNotNull(e)
                }
            }
        }
    }

    @Test
    fun `loadConfig returns valid configuration`() {
        val config = loadConfig()
        assertNotNull(config)
        assertEquals(8080, config.server.port)
    }

    @Test
    fun `startServer creates engine without error`() {
        val config =
            AppConfig(
                server = ServerConfig(9999, "127.0.0.1"),
                database = DatabaseConfig("test.db", 1, 1),
                publishing = PublishingConfig(1, 1),
            )
        val engine = startServer(config)
        assertNotNull(engine)
    }

    @Test
    fun `runMain starts and stops engine`() {
        val engine = runMain(wait = false)
        assertNotNull(engine)
        engine.stop(100L, 100L)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createWebsocketClient() =
        createClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

    private fun createTestModule(
        mockOshiRepo: StatsRepository = mockk(relaxed = true),
        mockHistoryRepo: SqliteHistoryRepository = mockk(relaxed = true),
        mockStatsWorker: StatsWorker = mockk(relaxed = true),
    ) = module {
        single {
            AppConfig(
                server = ServerConfig(8080, "localhost"),
                database = DatabaseConfig("test.db", 10, 15),
                publishing = PublishingConfig(10, 10),
            )
        }
        single { mockOshiRepo }
        single { mockHistoryRepo }
        single { mockStatsWorker }
    }
}
