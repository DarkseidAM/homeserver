package `in`.darkseid.homeserver

import com.typesafe.config.ConfigFactory
import `in`.darkseid.homeserver.di.configModule
import `in`.darkseid.homeserver.di.oshiModule
import `in`.darkseid.homeserver.di.serverModule
import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.workers.StatsWorker
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.application
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun main() {
    val rawConfig = ConfigFactory.load()

    @OptIn(ExperimentalSerializationApi::class)
    val appConfig = Hocon.decodeFromConfig<AppConfig>(rawConfig)

    embeddedServer(
        Netty,
        port = appConfig.server.port,
        host = appConfig.server.host,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(
            configModule,
            serverModule,
            oshiModule,
        )
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    val statsWorker by inject<StatsWorker>()
    statsWorker.start(this)

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
        configureStatsSocket(get(), statsWorker, get())
    }
}

fun Route.configureStatsSocket(
    repository: StatsRepository,
    statsWorker: StatsWorker,
    appConfig: AppConfig,
) {
    webSocket("/ws/cpu") {
        application.log.info("Client connected to CPU stream")
        runCatching {
            while (true) {
                // Fetch data (non-blocking thanks to withContext in repo)
                val stats = repository.getCpuStats()

                // Send as JSON
                sendSerialized(stats)

                // Wait 1 second before next update
                delay(appConfig.publishing.cpuStatsFrequency)
            }
        }.onFailure {
            application.log.error("Client disconnected from CPU stream: ${it.message}")
        }
    }
    webSocket("/ws/ram") {
        application.log.info("Client connected to RAM stream")
        runCatching {
            while (true) {
                val stats = repository.getRamStats()
                sendSerialized(stats)
                delay(appConfig.publishing.ramStatsFrequency)
            }
        }.onFailure {
            application.log.error("Client disconnected from RAM stream: ${it.message}")
        }
    }
    webSocket("/ws/system") {
        application.log.info("Client connected to System stream")
        runCatching {
            statsWorker.statsFlow.collect {
                sendSerialized(it)
            }
        }.onFailure {
            application.log.error("Client disconnected from System stream: ${it.message}")
        }
    }
}
