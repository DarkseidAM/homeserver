package `in`.darkseid.homeserver

import com.typesafe.config.ConfigFactory
import `in`.darkseid.homeserver.data.repository.SqliteHistoryRepository
import `in`.darkseid.homeserver.di.configModule
import `in`.darkseid.homeserver.di.oshiModule
import `in`.darkseid.homeserver.di.serverModule
import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.routes.configureStatsSocket
import `in`.darkseid.homeserver.workers.StatsWorker
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun main() {
    runMain(wait = true)
}

fun runMain(wait: Boolean): ApplicationEngine {
    val appConfig = loadConfig()
    val server = startServer(appConfig)
    server.start(wait = wait)
    return server.engine
}

@OptIn(ExperimentalSerializationApi::class)
fun loadConfig(): AppConfig {
    val rawConfig = ConfigFactory.load()
    return Hocon.decodeFromConfig(rawConfig)
}

fun startServer(appConfig: AppConfig): EmbeddedServer<*, *> =
    embeddedServer(
        Netty,
        port = appConfig.server.port,
        host = appConfig.server.host,
        module = Application::module,
    )

fun Application.module() {
    moduleWithDependencies(configModule, serverModule, oshiModule)
}

fun Application.moduleWithDependencies(vararg koinModules: Module) {
    install(Koin) {
        slf4jLogger()
        modules(koinModules.toList())
    }

    val historyRepository by inject<SqliteHistoryRepository>()
    historyRepository.init()

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
