package com.acrylic.outbound.pda.retention

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "local_audit_archive", primaryKeys = ["recoveryEpoch", "originDeviceId", "afterSeq"])
data class AuditArchive(val recoveryEpoch: String, val originDeviceId: String, val afterSeq: Long, val throughSeq: Long,
    val payloadSha256: String, val requestJson: String, val restoring: Boolean = false,
    val proofJson: String? = null, val signingKeyJson: String? = null)

@Dao
interface AuditArchiveDao {
    @Query("SELECT * FROM local_audit_archive WHERE recoveryEpoch=:epoch AND originDeviceId=:device ORDER BY afterSeq LIMIT 1025")
    fun rows(epoch: String, device: String): List<AuditArchive>
    @Query("SELECT * FROM local_audit_archive WHERE recoveryEpoch=:epoch AND originDeviceId=:device AND afterSeq=:after")
    fun get(epoch: String, device: String, after: Long): AuditArchive?
    @Query("""SELECT recoveryEpoch FROM local_audit_archive WHERE originDeviceId=:device AND CAST(recoveryEpoch AS INTEGER)<CAST(:epoch AS INTEGER)
        AND proofJson IS NOT NULL GROUP BY recoveryEpoch
        ORDER BY MAX(throughSeq) DESC, CAST(recoveryEpoch AS INTEGER) DESC LIMIT 1""")
    fun previousEpoch(epoch: String, device: String): String?
    @Insert fun insert(row: AuditArchive)
    @Update fun update(row: AuditArchive)
}

object AuditArchiveMigration : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_audit_archive(
            recoveryEpoch TEXT NOT NULL, originDeviceId TEXT NOT NULL, afterSeq INTEGER NOT NULL, throughSeq INTEGER NOT NULL,
            payloadSha256 TEXT NOT NULL, requestJson TEXT NOT NULL, restoring INTEGER NOT NULL,
            proofJson TEXT, signingKeyJson TEXT, PRIMARY KEY(recoveryEpoch,originDeviceId,afterSeq))""")
    }
}
