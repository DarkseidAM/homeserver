package `in`.darkseid.homeserver

import `in`.darkseid.homeserver.di.oshiModule
import `in`.darkseid.homeserver.di.serverModule
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.workers.StatsWorker
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(
            serverModule,
            oshiModule
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
        configureStatsSocket(get(), statsWorker)
    }
}

fun Route.configureStatsSocket(repository: StatsRepository, statsWorker: StatsWorker) {
    webSocket("/ws/cpu") {
        println("Client connected to CPU stream")
        runCatching {
            while (true) {
                // Fetch data (non-blocking thanks to withContext in repo)
                val stats = repository.getCpuStats()

                // Send as JSON
                sendSerialized(stats)

                // Wait 1 second before next update
                delay(1000)
            }
        }.onFailure {
            println("Client disconnected: ${it.message}")
        }
    }
    webSocket("/ws/ram") {
        println("Client connected to RAM stream")
        runCatching {
            while(true) {
                val stats = repository.getRamStats()
                sendSerialized(stats)
                delay(1000)
            }
        }.onFailure {
            println("Client disconnected: ${it.message}")
        }
    }
    webSocket("/ws/system") {
        println("Client connected to System stream")
        runCatching {
            statsWorker.statsFlow.collect {
                sendSerialized(it)
            }
        }.onFailure {
            println("Client disconnected: ${it.message}")
        }
    }
}
