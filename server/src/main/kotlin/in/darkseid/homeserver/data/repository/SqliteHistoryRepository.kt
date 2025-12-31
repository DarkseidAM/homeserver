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

class SqliteHistoryRepository {

    fun init() {
        Flyway.configure()
            .dataSource("jdbc:sqlite:data/server.db", "", "")
            .locations("classpath:db/migrations")
            .baselineOnMigrate(true)
            .load()
            .also {
                it.migrate()
            }
        Database.connect("jdbc:sqlite:data/server.db", "org.sqlite.JDBC")
    }

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

    fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        transaction {
            HistoryTable.deleteWhere { createdAt less cutoff }
        }
    }
}
