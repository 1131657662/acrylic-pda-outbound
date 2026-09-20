package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TemporaryMigration : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_request(`key` TEXT NOT NULL, userId TEXT NOT NULL,
            deviceId TEXT NOT NULL, warehouseId TEXT NOT NULL, recoveryEpoch TEXT NOT NULL, userAuthVersion TEXT NOT NULL,
            deviceVersion TEXT NOT NULL, bodyJson TEXT NOT NULL, signingKeyJson TEXT NOT NULL, receiptJson TEXT,
            PRIMARY KEY(`key`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_temporary_request_userId_deviceId_warehouseId ON temporary_request(userId,deviceId,warehouseId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_preparation(authorizationId TEXT NOT NULL, requestKey TEXT NOT NULL,
            bundleId TEXT NOT NULL, manifestSha256 TEXT NOT NULL, activatedAt TEXT NOT NULL, inventoryRows INTEGER NOT NULL,
            availabilityRows INTEGER NOT NULL, ackKey TEXT NOT NULL, ackBodyJson TEXT NOT NULL, ackJson TEXT,
            PRIMARY KEY(authorizationId), FOREIGN KEY(requestKey) REFERENCES temporary_request(`key`) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_temporary_preparation_requestKey ON temporary_preparation(requestKey)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS temporary_part_chunk(authorizationId TEXT NOT NULL, part TEXT NOT NULL,
            ordinal INTEGER NOT NULL, bytes BLOB NOT NULL, PRIMARY KEY(authorizationId,part,ordinal),
            FOREIGN KEY(authorizationId) REFERENCES temporary_preparation(authorizationId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
}
