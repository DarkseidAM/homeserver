package `in`.darkseid.homeserver.data.repository

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Statistics
import `in`.darkseid.homeserver.domain.models.ContainerStats
import `in`.darkseid.homeserver.utils.named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
class DockerStatsRepository(
    private val dockerClient: DockerClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + named("DockerStatsRepo"))

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
        val containers =
            dockerClient
                .listContainersCmd()
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
    private fun startMonitoring(
        id: String,
        name: String,
    ) {
        val callback =
            object : ResultCallback.Adapter<Statistics>() {
                override fun onNext(stats: Statistics?) {
                    scope.launch(dispatcher + named("Docker-Stats-$name")) {
                        stats?.let { s ->
                            processStatistics(s, id, name)?.let { containerStats ->
                                liveStats[id] = containerStats
                            }
                        }
                    }
                }
            }
        val stream = dockerClient.statsCmd(id).exec(callback)
        activeStreams[id] = stream
    }

    private fun processStatistics(
        s: Statistics,
        id: String,
        name: String,
    ): ContainerStats? {
        if (!hasValidCpuData(s)) return null

        val cpuPercent = calculateCpuPercent(s)
        val (rxBytes, txBytes) = calculateNetworkStats(s)
        val (blockRead, blockWrite) = calculateBlockIoStats(s)
        val pids = s.pidsStats?.current ?: 0L
        val cpuThrottled = (s.cpuStats?.throttlingData?.throttledPeriods ?: 0L) > 0L
        val memoryMaxUsage = s.memoryStats?.maxUsage ?: 0L

        return ContainerStats(
            id = id,
            name = name.removePrefix("/"),
            cpuPercent = cpuPercent,
            memoryUsageBytes = s.memoryStats?.usage ?: 0,
            memoryLimitBytes = s.memoryStats?.limit ?: 0,
            state = "running",
            networkRxBytes = rxBytes,
            networkTxBytes = txBytes,
            blockReadBytes = blockRead,
            blockWriteBytes = blockWrite,
            pids = pids,
            cpuThrottled = cpuThrottled,
            memoryMaxUsageBytes = memoryMaxUsage,
        )
    }

    private fun hasValidCpuData(s: Statistics): Boolean {
        val cpuUsage = s.cpuStats?.cpuUsage
        val preCpuUsage = s.preCpuStats?.cpuUsage
        return cpuUsage?.totalUsage != null &&
            preCpuUsage?.totalUsage != null &&
            s.cpuStats?.systemCpuUsage != null &&
            s.preCpuStats?.systemCpuUsage != null &&
            s.cpuStats?.onlineCpus != null
    }

    private fun calculateCpuPercent(s: Statistics): Double {
        val cpu = s.cpuStats
        val preCpu = s.preCpuStats
        val usage = cpu?.cpuUsage
        val preUsage = preCpu?.cpuUsage

        val total = usage?.totalUsage
        val preTotal = preUsage?.totalUsage
        val system = cpu?.systemCpuUsage
        val preSystem = preCpu?.systemCpuUsage
        val online = cpu?.onlineCpus

        return if (total != null && preTotal != null && system != null && preSystem != null && online != null) {
            val totalDelta = total - preTotal
            val systemDelta = system - preSystem
            if (systemDelta > 0.0 && totalDelta > 0.0) {
                (totalDelta.toDouble() / systemDelta.toDouble()) * online * CPU_PERCENTAGE_FACTOR
            } else {
                0.0
            }
        } else {
            0.0
        }
    }

    private fun calculateNetworkStats(s: Statistics): Pair<Long, Long> {
        var rxBytes = 0L
        var txBytes = 0L
        s.networks?.values?.forEach { net ->
            rxBytes += net.rxBytes ?: 0L
            txBytes += net.txBytes ?: 0L
        }
        return rxBytes to txBytes
    }

    private fun calculateBlockIoStats(s: Statistics): Pair<Long, Long> {
        var blockRead = 0L
        var blockWrite = 0L
        s.blkioStats?.ioServiceBytesRecursive?.forEach { entry ->
            when (entry.op?.lowercase()) {
                "read" -> blockRead += entry.value ?: 0L
                "write" -> blockWrite += entry.value ?: 0L
            }
        }
        return blockRead to blockWrite
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

    companion object {
        private const val CPU_PERCENTAGE_FACTOR = 100.0
    }
}
