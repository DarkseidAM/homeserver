package `in`.darkseid.homeserver.data.db

import org.jetbrains.exposed.sql.Table

object HistoryTable : Table("system_history") {
    val id = long("id").autoIncrement()
    val createdAt = long("created_at").index()

    val cpuLoad = double("cpu_load")
    val memoryUsed = long("memory_used")

    val containerJson = text("container_data")

    override val primaryKey = PrimaryKey(id)
}
