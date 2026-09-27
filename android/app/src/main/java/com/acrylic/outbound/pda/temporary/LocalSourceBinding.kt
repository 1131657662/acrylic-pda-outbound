package com.acrylic.outbound.pda.temporary

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The device's own view of what its TEMP sources are bound to now. It exists because a controlled link changes the
 * canonical order on the server; without this row the device would keep working against a superseded order. The row
 * is installed from GET /pda/temporary-source-bindings when installRequired is true.
 */
@Entity(tableName = "local_source_binding")
data class LocalSourceBinding(
    @PrimaryKey val scopeId: String,
    val clientOrderId: String,
    val canonicalOrderId: String,
    val mappingVersion: String,
    val mappingOrigin: String,
    val workRound: String,
    val phase: String,
    val orderNo: String,
    val installedAt: Long,
)

@Dao
interface LocalSourceBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun install(row: LocalSourceBinding)
    @Query("SELECT * FROM local_source_binding WHERE scopeId=:scopeId") fun get(scopeId: String): LocalSourceBinding?
    @Query("SELECT * FROM local_source_binding ORDER BY scopeId") fun all(): List<LocalSourceBinding>
    @Query("SELECT COUNT(*) FROM local_source_binding") fun count(): Int
}

/** V27 -> V28 adds one table only; existing tables and their rows are untouched. */
object LocalSourceBindingMigration : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_source_binding(scopeId TEXT NOT NULL PRIMARY KEY,
            clientOrderId TEXT NOT NULL,canonicalOrderId TEXT NOT NULL,mappingVersion TEXT NOT NULL,
            mappingOrigin TEXT NOT NULL,workRound TEXT NOT NULL,phase TEXT NOT NULL,orderNo TEXT NOT NULL,
            installedAt INTEGER NOT NULL)""")
    }
}
