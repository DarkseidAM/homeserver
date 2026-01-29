package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.data.db.HistoryTable
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Repository for managing system history data in a SQLite database.
 *
 * This repository handles initialization, saving snapshots, and pruning old data.
 */
class SqliteHistoryRepository(
    private val dbPath: String = "data/server.db",
) {
    companion object {
        private const val SEVEN_DAYS_IN_MS = 7 * 24 * 60 * 60 * 1000L
    }

    /**
     * Initializes the database connection and runs migrations.
     *
     * Configures Flyway to migrate the SQLite database located at "data/server.db"
     * and connects the Exposed framework to the same database.
     */
    fun init() {
        Flyway
            .configure()
            .dataSource("jdbc:sqlite:$dbPath", "", "")
            .locations("classpath:db/migrations")
            .baselineOnMigrate(true)
            .load()
            .apply {
                migrate()
            }
        Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
    }

    /**
     * Saves a full system snapshot to the database.
     *
     * @param snapshot The [FullSystemSnapshot] to save.
     */
    fun saveSnapshot(snapshot: FullSystemSnapshot) {
        transaction {
            HistoryTable.insert {
                it[createdAt] = snapshot.timestamp
                it[cpuLoad] = snapshot.cpu.usagePercent
                it[memoryUsed] = snapshot.ram.used
                it[containerJson] = Json.encodeToString(snapshot.containers)
                it[storageJson] = Json.encodeToString(snapshot.storage)
                it[networkJson] = Json.encodeToString(snapshot.network)
                it[systemJson] = Json.encodeToString(snapshot.system)
            }
        }
    }

    /**
     * Saves a list of full system snapshots to the database in a batch.
     *
     * @param snapshots The list of [FullSystemSnapshot] to save.
     */
    fun saveSnapshots(snapshots: List<FullSystemSnapshot>) {
        if (snapshots.isEmpty()) return
        transaction {
            HistoryTable.batchInsert(snapshots) { snapshot ->
                this[HistoryTable.createdAt] = snapshot.timestamp
                this[HistoryTable.cpuLoad] = snapshot.cpu.usagePercent
                this[HistoryTable.memoryUsed] = snapshot.ram.used
                this[HistoryTable.containerJson] = Json.encodeToString(snapshot.containers)
                this[HistoryTable.storageJson] = Json.encodeToString(snapshot.storage)
                this[HistoryTable.networkJson] = Json.encodeToString(snapshot.network)
                this[HistoryTable.systemJson] = Json.encodeToString(snapshot.system)
            }
        }
    }

    /**
     * Prunes data older than 7 days from the database.
     */
    fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_IN_MS
        transaction {
            HistoryTable.deleteWhere { createdAt less cutoff }
        }
    }
}
