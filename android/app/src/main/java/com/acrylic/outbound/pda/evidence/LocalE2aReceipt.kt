package com.acrylic.outbound.pda.evidence

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "local_e2a_receipt", foreignKeys = [ForeignKey(entity = LocalE2aRequest::class,
    parentColumns = ["originalKey"], childColumns = ["originalKey"])])
data class LocalE2aReceipt(@PrimaryKey val originalKey: String, val state: String,
    val responseBytes: ByteArray? = null, val signingKeyJson: String? = null, val error: String = "")

@Entity(tableName = "local_e2a_queue", foreignKeys = [ForeignKey(entity = LocalE2aTransfer::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalE2aQueue(@PrimaryKey val clientOrderId: String, val state: String = "READY",
    val autoRetry: Boolean = true, val error: String = "")

@Dao
interface LocalE2aReceiptDao {
    @Query("SELECT * FROM local_e2a_receipt WHERE originalKey=:key") fun get(key: String): LocalE2aReceipt?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(row: LocalE2aReceipt)
    @Update fun update(row: LocalE2aReceipt)
    @Query("SELECT * FROM local_e2a_queue WHERE clientOrderId=:id") fun queue(id: String): LocalE2aQueue?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertQueue(row: LocalE2aQueue)
    @Update fun updateQueue(row: LocalE2aQueue)
    @Query("""SELECT q.clientOrderId FROM local_e2a_queue q
        INNER JOIN local_e2a_stop s ON s.clientOrderId=q.clientOrderId
        WHERE q.autoRetry=1 AND s.originalOperatorId=:user AND s.freezeDeviceId=:device
        AND s.warehouseId=:warehouse AND s.sourceRecoveryEpoch=:epoch ORDER BY q.clientOrderId LIMIT 20""")
    fun pending(user: String, device: String, warehouse: String, epoch: Long): List<String>
}

/** Upgrades preserve all old requests; transmission starts only after an explicit queued recovery. */
object LocalE2aReceiptMigration : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_receipt(originalKey TEXT NOT NULL PRIMARY KEY,
            state TEXT NOT NULL,responseBytes BLOB,signingKeyJson TEXT,error TEXT NOT NULL,
            FOREIGN KEY(originalKey) REFERENCES local_e2a_request(originalKey) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_e2a_queue(clientOrderId TEXT NOT NULL PRIMARY KEY,
            state TEXT NOT NULL,autoRetry INTEGER NOT NULL,error TEXT NOT NULL,
            FOREIGN KEY(clientOrderId) REFERENCES local_e2a_transfer(clientOrderId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
