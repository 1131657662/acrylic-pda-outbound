package com.acrylic.outbound.pda.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "claim_intent_history", indices = [Index(value = ["orderId", "userId", "deviceId"]),
    Index(value = ["userId", "deviceId", "archivedAt"])])
data class ClaimIntentHistory(@PrimaryKey val key: String, val orderId: String, val userId: String,
    val deviceId: String, val body: String, val recoveryEpoch: String, val status: String,
    val lastError: String?, val archivedAt: Long)

object ClaimIntentHistoryMigration : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE claim_intent_next(orderId TEXT NOT NULL,userId TEXT NOT NULL,deviceId TEXT NOT NULL,
            `key` TEXT NOT NULL,body TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'UNKNOWN',
            lastError TEXT,PRIMARY KEY(orderId,userId,deviceId))""")
        db.execSQL("""INSERT INTO claim_intent_next(orderId,userId,deviceId,`key`,body,recoveryEpoch,status,lastError)
            SELECT orderId,userId,deviceId,`key`,body,recoveryEpoch,status,lastError FROM claim_intent""")
        db.execSQL("DROP TABLE claim_intent")
        db.execSQL("ALTER TABLE claim_intent_next RENAME TO claim_intent")
        db.execSQL("""CREATE TABLE claim_intent_history(`key` TEXT NOT NULL PRIMARY KEY,orderId TEXT NOT NULL,
            userId TEXT NOT NULL,deviceId TEXT NOT NULL,body TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,
            status TEXT NOT NULL,lastError TEXT,archivedAt INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX index_claim_intent_history_orderId_userId_deviceId ON claim_intent_history(orderId,userId,deviceId)")
        db.execSQL("CREATE INDEX index_claim_intent_history_userId_deviceId_archivedAt ON claim_intent_history(userId,deviceId,archivedAt)")
        db.execSQL("CREATE INDEX index_prepared_order_activatedAt_assignmentId ON prepared_order(activatedAt,assignmentId)")
    }
}
