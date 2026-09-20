package com.acrylic.outbound.pda.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BarcodeMigration : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_cloud_barcode(
            serverRecoveryEpoch TEXT NOT NULL, warehouseId TEXT NOT NULL, baselineId TEXT NOT NULL,
            barcode TEXT NOT NULL, baleId TEXT NOT NULL, status TEXT NOT NULL, dispatchCycle INTEGER NOT NULL,
            rowVersion INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(serverRecoveryEpoch,warehouseId,baselineId,barcode))""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS index_local_cloud_barcode_serverRecoveryEpoch_warehouseId_baselineId_baleId
            ON local_cloud_barcode(serverRecoveryEpoch,warehouseId,baselineId,baleId)""")
    }
}
