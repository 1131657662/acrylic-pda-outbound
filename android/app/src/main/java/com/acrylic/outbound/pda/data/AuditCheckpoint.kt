package com.acrylic.outbound.pda.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.work.WorkEvent

@Entity(tableName = "local_audit_checkpoint", primaryKeys = ["recoveryEpoch", "originDeviceId"])
data class AuditCheckpoint(val recoveryEpoch: String, val originDeviceId: String,
    val acknowledgedThrough: Long = 0, val pendingJson: String? = null)

@Dao
interface AuditDao {
    @Query("SELECT * FROM local_audit_checkpoint WHERE recoveryEpoch=:epoch AND originDeviceId=:device")
    fun checkpoint(epoch: String, device: String): AuditCheckpoint?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(row: AuditCheckpoint)
    @Query("SELECT * FROM work_event WHERE originDeviceId=:device AND localSeq>:after ORDER BY localSeq LIMIT 200")
    fun page(device: String, after: Long): List<WorkEvent>
    @Query("SELECT COUNT(*) FROM work_event WHERE originDeviceId=:device AND localSeq>:after AND localSeq<=:through")
    fun coverage(device: String, after: Long, through: Long): Long
}

object AuditMigration : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_audit_checkpoint(
            recoveryEpoch TEXT NOT NULL, originDeviceId TEXT NOT NULL, acknowledgedThrough INTEGER NOT NULL,
            pendingJson TEXT, PRIMARY KEY(recoveryEpoch,originDeviceId))""")
    }
}
