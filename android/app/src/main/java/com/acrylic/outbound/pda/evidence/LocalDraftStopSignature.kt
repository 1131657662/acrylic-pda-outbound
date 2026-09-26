package com.acrylic.outbound.pda.evidence

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "local_draft_stop_signature", foreignKeys = [ForeignKey(entity = LocalDraftStop::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalDraftStopSignature(@PrimaryKey val clientOrderId: String, val evidenceId: String,
    val publicKey: String, val proofJson: String)

@Dao
interface LocalDraftStopSignatureDao {
    @Insert fun insert(row: LocalDraftStopSignature)
    @Query("SELECT * FROM local_draft_stop_signature WHERE clientOrderId=:id")
    fun get(id: String): LocalDraftStopSignature?
}

object LocalDraftStopSignatureMigration : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_stop_signature(
            clientOrderId TEXT NOT NULL PRIMARY KEY,evidenceId TEXT NOT NULL,publicKey TEXT NOT NULL,proofJson TEXT NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES local_draft_stop(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
