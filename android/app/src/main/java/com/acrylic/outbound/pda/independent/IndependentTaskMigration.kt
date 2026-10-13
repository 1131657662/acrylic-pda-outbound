package com.acrylic.outbound.pda.independent

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object IndependentTaskMigration : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_task(
            id TEXT NOT NULL PRIMARY KEY,importId TEXT NOT NULL,skuSha256 TEXT NOT NULL,
            credentialId TEXT NOT NULL,signedCredential TEXT NOT NULL,trustedKeyJson TEXT NOT NULL,timeAnchorJson TEXT NOT NULL,localSessionId TEXT NOT NULL,
            operatorId TEXT NOT NULL,deviceId TEXT NOT NULL,warehouseId TEXT NOT NULL,recoveryEpoch TEXT NOT NULL,
            vehicleNo TEXT NOT NULL,reason TEXT NOT NULL,plannedTotal INTEGER NOT NULL,commandSha256 TEXT NOT NULL,
            createdWall INTEGER NOT NULL,createdElapsed INTEGER NOT NULL,createdBoot INTEGER NOT NULL,
            FOREIGN KEY(importId) REFERENCES independent_sku_import(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_task_importId ON independent_task(importId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_task_operatorId_deviceId_warehouseId ON independent_task(operatorId,deviceId,warehouseId)")
        lines(db); lifecycle(db); requests(db)
    }
    private fun lines(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_task_line(
            taskId TEXT NOT NULL,deliveryCode TEXT NOT NULL,model TEXT NOT NULL,name TEXT NOT NULL,plannedCount INTEGER NOT NULL,
            PRIMARY KEY(taskId,deliveryCode),
            FOREIGN KEY(taskId) REFERENCES independent_task(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
    }
    private fun lifecycle(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_task_state(
            taskId TEXT NOT NULL PRIMARY KEY,state TEXT NOT NULL,nextScanSeq INTEGER NOT NULL,nextEventSeq INTEGER NOT NULL,
            actualCount INTEGER NOT NULL,weightGrams INTEGER NOT NULL,
            FOREIGN KEY(taskId) REFERENCES independent_task(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_task_event(
            id TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,sequence INTEGER NOT NULL,type TEXT NOT NULL,
            detail TEXT NOT NULL,wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot INTEGER NOT NULL,
            FOREIGN KEY(taskId) REFERENCES independent_task(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_independent_task_event_taskId_sequence ON independent_task_event(taskId,sequence)")
    }
    private fun requests(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS independent_task_request(
            id TEXT NOT NULL PRIMARY KEY,importId TEXT NOT NULL,commandJson TEXT NOT NULL,commandSha256 TEXT NOT NULL,
            authorizationJson TEXT NOT NULL,operatorId TEXT NOT NULL,deviceId TEXT NOT NULL,warehouseId TEXT NOT NULL,
            FOREIGN KEY(importId) REFERENCES independent_sku_import(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_task_request_importId ON independent_task_request(importId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_independent_task_request_operatorId_deviceId_warehouseId ON independent_task_request(operatorId,deviceId,warehouseId)")
    }
}
