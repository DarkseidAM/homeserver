package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.data.db.HistoryTable
import `in`.darkseid.homeserver.domain.models.ContainerStats
import `in`.darkseid.homeserver.domain.models.CpuStats
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import `in`.darkseid.homeserver.domain.models.RamStats
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
    fun `pruneOldData removes old records`() {
        val oldTimestamp = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
        val newTimestamp = System.currentTimeMillis()

        val oldSnapshot =
            FullSystemSnapshot(
                timestamp = oldTimestamp,
                cpu = CpuStats("Old", 4, 4, 10.0, 40.0),
                ram = RamStats(100L, 50L, 50L, 50.0),
                containers = emptyList(),
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
