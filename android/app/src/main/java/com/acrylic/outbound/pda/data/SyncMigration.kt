package com.acrylic.outbound.pda.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SyncMigration : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_sync_state(
            serverRecoveryEpoch TEXT NOT NULL, warehouseId TEXT NOT NULL, streamType TEXT NOT NULL,
            cursor TEXT, sequence INTEGER NOT NULL, activeBaselineId TEXT, downloadId TEXT, downloadCursor TEXT,
            downloadWatermark INTEGER, lastSuccessAt TEXT, PRIMARY KEY(serverRecoveryEpoch,warehouseId,streamType))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_cloud_order(
            serverRecoveryEpoch TEXT NOT NULL, warehouseId TEXT NOT NULL, baselineId TEXT NOT NULL,
            orderId TEXT NOT NULL, status TEXT NOT NULL, rowVersion INTEGER NOT NULL, updatedAt TEXT NOT NULL,
            PRIMARY KEY(serverRecoveryEpoch,warehouseId,baselineId,orderId))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_recovery_control(
            singletonId INTEGER NOT NULL PRIMARY KEY, observedServerEpoch TEXT NOT NULL, mode TEXT NOT NULL, recoverySessionId TEXT)""")
    }
}
