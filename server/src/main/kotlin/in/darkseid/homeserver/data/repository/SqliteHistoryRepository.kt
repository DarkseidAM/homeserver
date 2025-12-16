package `in`.darkseid.homeserver.data.repository

import `in`.darkseid.homeserver.data.db.HistoryTable
import `in`.darkseid.homeserver.domain.models.FullSystemSnapshot
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class SqliteHistoryRepository {

    fun init() {
        Database.connect("jdbc:sqlite:data/server.db", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(HistoryTable)
        }
    }

    fun saveSnapshot(snapshot: FullSystemSnapshot) {
        transaction {
            HistoryTable.insert {
                it[createdAt] = snapshot.timestamp
                it[cpuLoad] = snapshot.cpu.usagePercent
                it[memoryUsed] = 0 // Add memory to your CpuStats or SystemStats model
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
