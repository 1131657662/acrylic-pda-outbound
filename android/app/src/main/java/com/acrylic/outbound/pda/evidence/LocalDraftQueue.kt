package com.acrylic.outbound.pda.evidence

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "local_draft_queue", foreignKeys = [ForeignKey(entity = LocalDraftTransfer::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalDraftQueue(@PrimaryKey val clientOrderId: String, val state: String = "READY",
    val verifiedRequests: Long = 0, val totalRequests: Long, val autoRetry: Boolean = true, val error: String = "")

@Dao
interface LocalDraftQueueDao {
    @Insert fun insert(row: LocalDraftQueue)
    @Update fun update(row: LocalDraftQueue)
    @Query("SELECT * FROM local_draft_queue WHERE clientOrderId=:id") fun get(id: String): LocalDraftQueue?
    @Query("UPDATE local_draft_queue SET state='ACTION_REQUIRED',autoRetry=0,error=:reason WHERE autoRetry=1")
    fun pauseAll(reason: String)
    @Query("SELECT * FROM local_draft_queue WHERE autoRetry=1 AND clientOrderId>:after ORDER BY clientOrderId LIMIT 100")
    fun enabled(after: String): List<LocalDraftQueue>
    @Query("""SELECT q.clientOrderId FROM local_draft_queue q JOIN temporary_draft d ON d.clientOrderId=q.clientOrderId
        WHERE q.autoRetry=1 AND d.creatorDeviceId=:device AND d.operatorId=:user AND d.warehouseId=:warehouse
        ORDER BY q.clientOrderId LIMIT 20""")
    fun pending(device: String, user: String, warehouse: String): List<String>
}

/** Enabling automatic transfer is explicit; migrations never enqueue historical stopped drafts. */
object LocalDraftQueueMigration : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_queue(clientOrderId TEXT NOT NULL PRIMARY KEY,state TEXT NOT NULL,
            verifiedRequests INTEGER NOT NULL,totalRequests INTEGER NOT NULL,autoRetry INTEGER NOT NULL,error TEXT NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES local_draft_transfer(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
