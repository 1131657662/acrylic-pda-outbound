package com.acrylic.outbound.pda.retention

import androidx.room.Entity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Times are trusted server instants in epoch milliseconds, never business row updatedAt. */
@Entity(tableName = "local_retained_baseline",
    primaryKeys = ["serverRecoveryEpoch", "warehouseId", "streamType", "baselineId"])
data class RetainedBaseline(val serverRecoveryEpoch: String, val warehouseId: String, val streamType: String,
    val baselineId: String, val successObservedAt: Long, val retiredAt: Long? = null)

object RetentionMigration : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_retained_baseline(
            serverRecoveryEpoch TEXT NOT NULL, warehouseId TEXT NOT NULL, streamType TEXT NOT NULL,
            baselineId TEXT NOT NULL, successObservedAt INTEGER NOT NULL, retiredAt INTEGER,
            PRIMARY KEY(serverRecoveryEpoch,warehouseId,streamType,baselineId))""")
    }
}
