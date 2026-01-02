package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.data.db.HistoryTable
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Repository for managing system history data in a SQLite database.
 *
 * This repository handles initialization, saving snapshots, and pruning old data.
 */
class SqliteHistoryRepository(private val dbPath: String = "data/server.db") {

    /**
     * Initializes the database connection and runs migrations.
     *
     * Configures Flyway to migrate the SQLite database located at "data/server.db"
     * and connects the Exposed framework to the same database.
     */
    fun init() {
        Flyway.configure()
            .dataSource("jdbc:sqlite:${dbPath}", "", "")
            .locations("classpath:db/migrations")
            .baselineOnMigrate(true)
            .load()
            .apply {
                migrate()
            }
        Database.connect("jdbc:sqlite:data/server.db", "org.sqlite.JDBC")
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
            }
        }
    }

    /**
     * Prunes data older than 7 days from the database.
     */
    fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        transaction {
            HistoryTable.deleteWhere { createdAt less cutoff }
        }
    }
}
