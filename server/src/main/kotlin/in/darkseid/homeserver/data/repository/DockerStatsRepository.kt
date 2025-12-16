package `in`.darkseid.homeserver.data.repository

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Statistics
import `in`.darkseid.homeserver.domain.models.ContainerStats
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class DockerStatsRepository(private val dockerClient: DockerClient) : Closeable {
    private val liveStats = ConcurrentHashMap<String, ContainerStats>()
    private val activeStreams = ConcurrentHashMap<String, Closeable>()

    fun getLatestStats(): List<ContainerStats> {
        val containers = dockerClient.listContainersCmd()
            .withStatusFilter(listOf("running"))
            .exec()

        containers.forEach { container ->
            if (!activeStreams.containsKey(container.id)) {
                startMonitoring(container.id, container.names.firstOrNull() ?: "unknown")
            }
        }

        val runningIds = containers.map { it.id }.toSet()
        val stoppedIds = activeStreams.keys - runningIds

        stoppedIds.forEach { id ->
            activeStreams.remove(id)?.close()
            liveStats.remove(id)
        }

        return liveStats.values.toList()
    }

    private fun startMonitoring(id: String, name: String) {
        val callback = object : ResultCallback.Adapter<Statistics>() {
            override fun onNext(stats: Statistics?) {
                stats?.let { s ->
                    val totalUsage = s.cpuStats?.cpuUsage?.totalUsage
                    val preTotalUsage = s.preCpuStats?.cpuUsage?.totalUsage
                    val systemUsage = s.cpuStats?.systemCpuUsage
                    val preSystemUsage = s.preCpuStats?.systemCpuUsage
                    val onlineCpus = s.cpuStats?.onlineCpus

                    // A valid CPU percentage can only be calculated if all values are present and deltas are positive.
                    if (totalUsage != null && preTotalUsage != null && systemUsage != null && preSystemUsage != null && onlineCpus != null) {
                        val cpuDelta = totalUsage - preTotalUsage
                        val systemDelta = systemUsage - preSystemUsage

                        val cpuPercent = if (systemDelta > 0.0 && cpuDelta > 0.0) {
                            (cpuDelta.toDouble() / systemDelta.toDouble()) * onlineCpus * 100.0
                        } else {
                            0.0
                        }

                        liveStats[id] = ContainerStats(
                            id = id,
                            name = name.removePrefix("/"),
                            cpuPercent = cpuPercent,
                            memoryUsageBytes = s.memoryStats?.usage ?: 0,
                            memoryLimitBytes = s.memoryStats?.limit ?: 0,
                            state = "running"
                        )
                    }
                }
            }
        }
        val stream = dockerClient.statsCmd(id).exec(callback)
        activeStreams[id] = stream
    }

    override fun close() {
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
    }
}
