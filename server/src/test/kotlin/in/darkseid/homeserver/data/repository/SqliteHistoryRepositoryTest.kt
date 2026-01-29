package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.data.db.HistoryTable
import `in`.darkseid.homeserver.domain.models.ContainerStats
import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import `in`.darkseid.homeserver.domain.models.NetworkStats
import `in`.darkseid.homeserver.domain.models.RamStats
import `in`.darkseid.homeserver.domain.models.StorageStats
import `in`.darkseid.homeserver.domain.models.SystemStats
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqliteHistoryRepositoryTest {
    private lateinit var dbFile: File
    private lateinit var repository: SqliteHistoryRepository

    private val dummyStorage = StorageStats(emptyList(), emptyList())
    private val dummyNetwork = NetworkStats(emptyList())
    private val dummySystem =
        SystemStats(
            "Mac",
            "Apple",
            "13.0",
            "Apple",
            "MacBook",
            "M1",
            100L,
            100,
            10,
            1.0,
            emptyList(),
        )

    @Before
    fun setup() {
        // Create a temp file for the DB
        dbFile = File.createTempFile("test_homeserver_", ".db")
        repository = SqliteHistoryRepository(dbFile.absolutePath)
        repository.init()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `saveSnapshot inserts data correctly`() {
        val snapshot =
            FullSystemSnapshot(
                timestamp = System.currentTimeMillis(),
                cpu = CpuStats("M1", 8, 8, 10.0, 45.0),
                ram = RamStats(1000L, 500L, 500L, 50.0),
                containers =
                    listOf(
                        ContainerStats("1", "test", 0.5, 100L, 200L, "running"),
                    ),
                storage = dummyStorage,
                network = dummyNetwork,
                system = dummySystem,
            )

        repository.saveSnapshot(snapshot)

        transaction {
            val rows = HistoryTable.selectAll().toList()
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals(snapshot.timestamp, row[HistoryTable.createdAt])
            assertEquals(snapshot.cpu.usagePercent, row[HistoryTable.cpuLoad])
            assertEquals(snapshot.ram.used, row[HistoryTable.memoryUsed])
            assertTrue(row[HistoryTable.containerJson].contains("test"))
        }
    }

    @Test
    fun `saveSnapshots inserts multiple records correctly`() {
        val now = System.currentTimeMillis()
        val snapshot1 =
            FullSystemSnapshot(
                timestamp = now,
                cpu = CpuStats("M1", 8, 8, 10.0, 45.0),
                ram = RamStats(1000L, 500L, 500L, 50.0),
                containers = emptyList(),
                storage = dummyStorage,
                network = dummyNetwork,
                system = dummySystem,
            )
        val snapshot2 = snapshot1.copy(timestamp = now + 1000)

        repository.saveSnapshots(listOf(snapshot1, snapshot2))

        transaction {
            val rows = HistoryTable.selectAll().orderBy(HistoryTable.createdAt).toList()
            assertEquals(2, rows.size)
            assertEquals(snapshot1.timestamp, rows[0][HistoryTable.createdAt])
            assertEquals(snapshot2.timestamp, rows[1][HistoryTable.createdAt])
        }
    }

    @Test
    fun `pruneOldData removes old records`() {
        val oldTimestamp = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
        val newTimestamp = System.currentTimeMillis()

        val oldSnapshot =
            FullSystemSnapshot(
                timestamp = oldTimestamp,
                cpu = CpuStats("Old", 4, 4, 10.0, 40.0),
                ram = RamStats(100L, 50L, 50L, 50.0),
                containers = emptyList(),
                storage = dummyStorage,
                network = dummyNetwork,
                system = dummySystem,
            )
        val newSnapshot = oldSnapshot.copy(timestamp = newTimestamp)

        repository.saveSnapshot(oldSnapshot)
        repository.saveSnapshot(newSnapshot)

        // Verify both exist
        transaction {
            assertEquals(2, HistoryTable.selectAll().count())
        }

        repository.pruneOldData()

        // Verify only new one exists
        transaction {
            val rows = HistoryTable.selectAll().toList()
            assertEquals(1, rows.size)
            assertEquals(newTimestamp, rows.first()[HistoryTable.createdAt])
        }
    }
}
