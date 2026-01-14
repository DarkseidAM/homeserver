package `in`.darkseid.homeserver.data.repository

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Statistics
import `in`.darkseid.homeserver.domain.models.ContainerStats
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for retrieving and managing Docker container statistics.
 *
 * This repository maintains real-time statistics for running Docker containers by establishing
 * streams for each container. It handles the lifecycle of these streams, ensuring that
 * new containers are monitored and stopped containers are cleaned up.
 *
 * @property dockerClient The [DockerClient] instance used to interact with the Docker daemon.
 */
class DockerStatsRepository(private val dockerClient: DockerClient) : Closeable {
    /**
     * Stores the latest statistics for each container, keyed by container ID.
     * Uses [ConcurrentHashMap] for thread-safe access.
     */
    private val liveStats = ConcurrentHashMap<String, ContainerStats>()

    /**
     * Stores the active closeable streams for each container, keyed by container ID.
     * Uses [ConcurrentHashMap] for thread-safe access.
     */
    private val activeStreams = ConcurrentHashMap<String, Closeable>()

    /**
     * Retrieves the latest statistics for all currently running containers.
     *
     * This method performs the following actions:
     * 1. Lists all currently running containers.
     * 2. Starts monitoring any new containers that are not yet being tracked.
     * 3. Stops monitoring and cleans up resources for containers that are no longer running.
     * 4. Returns the current snapshot of statistics for all tracked containers.
     *
     * @return A list of [ContainerStats] representing the latest state of running containers.
     */
    fun getLatestStats(): List<ContainerStats> {
        val containers = dockerClient.listContainersCmd()
            .withStatusFilter(listOf("running"))
            .exec()
            .toList()

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

    /**
     * Starts a statistics stream for a specific container.
     *
     * This method attaches a callback to the Docker stats command for the given container ID.
     * The callback calculates CPU usage percentage and updates the [liveStats] map with the
     * latest [ContainerStats].
     *
     * @param id The ID of the container to monitor.
     * @param name The name of the container.
     */
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

    /**
     * Closes all active statistic streams and clears the internal state.
     *
     * This method should be called when the repository is no longer needed to release resources.
     */
    override fun close() {
        activeStreams.values.forEach { it.close() }
        activeStreams.clear()
    }
}
