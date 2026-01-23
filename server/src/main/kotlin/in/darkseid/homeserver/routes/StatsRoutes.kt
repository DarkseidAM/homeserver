package `in`.darkseid.homeserver.routes

import `in`.darkseid.homeserver.domain.models.AppConfig
import `in`.darkseid.homeserver.domain.repository.StatsRepository
import `in`.darkseid.homeserver.utils.named
import `in`.darkseid.homeserver.workers.StatsWorker
import io.ktor.server.application.log
import io.ktor.server.routing.Route
import io.ktor.server.websocket.application
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

fun Route.configureStatsSocket(
    repository: StatsRepository,
    statsWorker: StatsWorker,
    appConfig: AppConfig,
) {
    configureCpuSocket(repository, appConfig)
    configureRamSocket(repository, appConfig)
    configureSystemSocket(statsWorker)
}

private fun Route.configureCpuSocket(
    repository: StatsRepository,
    appConfig: AppConfig,
) {
    webSocket("/ws/cpu") {
        withContext(named("WS-Cpu")) {
            application.log.info("Client connected to CPU stream")
            try {
                while (true) {
                    val stats = repository.getCpuStats()
                    sendSerialized(stats)
                    delay(appConfig.publishing.cpuStatsFrequency)
                }
            } catch (e: ClosedReceiveChannelException) {
                application.log.info("Client disconnected gracefully: ${e.message}")
            } catch (e: ClosedSendChannelException) {
                application.log.info("Client disconnected (Channel Closed): ${e.message}")
            } catch (e: IOException) {
                application.log.info("Client disconnected from CPU stream (IO Error: ${e.message})")
            }
        }
    }
}

private fun Route.configureRamSocket(
    repository: StatsRepository,
    appConfig: AppConfig,
) {
    webSocket("/ws/ram") {
        withContext(named("WS-Ram")) {
            application.log.info("Client connected to RAM stream")
            try {
                while (true) {
                    val stats = repository.getRamStats()
                    sendSerialized(stats)
                    delay(appConfig.publishing.ramStatsFrequency)
                }
            } catch (e: ClosedReceiveChannelException) {
                application.log.info("Client disconnected gracefully: ${e.message}")
            } catch (e: ClosedSendChannelException) {
                application.log.info("Client disconnected (Channel Closed): ${e.message}")
            } catch (e: IOException) {
                application.log.info("Client disconnected from RAM stream (IO Error: ${e.message})")
            }
        }
    }
}

private fun Route.configureSystemSocket(statsWorker: StatsWorker) {
    webSocket("/ws/system") {
        withContext(named("WS-System")) {
            application.log.info("Client connected to System stream")
            try {
                statsWorker.statsFlow.collect {
                    sendSerialized(it)
                }
            } catch (e: ClosedReceiveChannelException) {
                application.log.info("Client disconnected gracefully: ${e.message}")
            } catch (e: ClosedSendChannelException) {
                application.log.info("Client disconnected (Channel Closed): ${e.message}")
            } catch (e: IOException) {
                application.log.info("Client disconnected from System stream (IO Error: ${e.message})")
            }
        }
    }
}
