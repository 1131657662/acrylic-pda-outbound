package com.acrylic.outbound.pda.independent

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object IndependentScanMigration : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) { captures(db); scans(db); outcomes(db) }
    private fun captures(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE independent_capture(id TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,sequence INTEGER NOT NULL,
            deviceId TEXT NOT NULL,deviceSequence INTEGER NOT NULL,rawInput TEXT NOT NULL,barcode TEXT NOT NULL,inputSource TEXT NOT NULL,
            wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot INTEGER NOT NULL,capturedTrustedAt TEXT NOT NULL,
            FOREIGN KEY(taskId) REFERENCES independent_task(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_independent_capture_taskId_sequence ON independent_capture(taskId,sequence)")
        db.execSQL("CREATE UNIQUE INDEX index_independent_capture_deviceId_deviceSequence ON independent_capture(deviceId,deviceSequence)")
        db.execSQL("""CREATE TABLE independent_capture_state(captureId TEXT NOT NULL PRIMARY KEY,state TEXT NOT NULL,error TEXT NOT NULL,
            confirmedAt TEXT,failureAckAt TEXT,FOREIGN KEY(captureId) REFERENCES independent_capture(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE TABLE independent_device_state(deviceId TEXT NOT NULL PRIMARY KEY,nextCaptureSeq INTEGER NOT NULL)")
    }
    private fun scans(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE independent_scan(captureId TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,sequence INTEGER NOT NULL,
            barcode TEXT NOT NULL,deliveryCode TEXT NOT NULL,model TEXT NOT NULL,name TEXT NOT NULL,rawWeight TEXT NOT NULL,weightGrams INTEGER NOT NULL,
            skuLineNo INTEGER NOT NULL,skuRawText TEXT NOT NULL,savedTrustedAt TEXT NOT NULL,
            FOREIGN KEY(captureId) REFERENCES independent_capture(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX index_independent_scan_taskId ON independent_scan(taskId)")
        db.execSQL("CREATE INDEX index_independent_scan_barcode ON independent_scan(barcode)")
        db.execSQL("""CREATE TABLE independent_guard(barcode TEXT NOT NULL PRIMARY KEY,captureId TEXT NOT NULL,taskId TEXT NOT NULL,
            FOREIGN KEY(captureId) REFERENCES independent_scan(captureId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_independent_guard_captureId ON independent_guard(captureId)")
        db.execSQL("CREATE INDEX index_independent_guard_taskId ON independent_guard(taskId)")
    }
    private fun outcomes(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE independent_void(captureId TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,reason TEXT NOT NULL,
            wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot INTEGER NOT NULL,authorizedAt TEXT NOT NULL,
            FOREIGN KEY(captureId) REFERENCES independent_scan(captureId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX index_independent_void_taskId ON independent_void(taskId)")
    }
}
