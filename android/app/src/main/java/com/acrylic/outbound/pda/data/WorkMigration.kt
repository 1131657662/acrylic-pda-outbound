package com.acrylic.outbound.pda.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WorkMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createOrders(db); createCaptures(db); createScans(db); createEvents(db)
    }

    private fun createOrders(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE work_order (assignmentId TEXT NOT NULL PRIMARY KEY,orderId TEXT NOT NULL,
            vehicleNo TEXT NOT NULL,plannedTotal INTEGER NOT NULL,nextOrderScanSeq INTEGER NOT NULL,
            actualCount INTEGER NOT NULL,weightGrams INTEGER NOT NULL,startedAt TEXT,state TEXT NOT NULL,
            FOREIGN KEY(assignmentId) REFERENCES prepared_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_work_order_orderId ON work_order(orderId)")
        db.execSQL("""CREATE TABLE work_line (assignmentId TEXT NOT NULL,deliveryCode TEXT NOT NULL,model TEXT NOT NULL,
            name TEXT NOT NULL,plannedCount INTEGER NOT NULL,actualCount INTEGER NOT NULL,weightGrams INTEGER NOT NULL,
            PRIMARY KEY(assignmentId,deliveryCode),FOREIGN KEY(assignmentId) REFERENCES work_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE TABLE work_device (deviceId TEXT NOT NULL PRIMARY KEY,nextCaptureSeq INTEGER NOT NULL,nextEventSeq INTEGER NOT NULL)")
    }

    private fun createCaptures(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE work_capture (id TEXT NOT NULL PRIMARY KEY,
            assignmentId TEXT NOT NULL,orderId TEXT NOT NULL,originDeviceId TEXT NOT NULL,operatorId TEXT NOT NULL,
            warehouseId TEXT NOT NULL,orderScanSeq INTEGER NOT NULL,deviceCaptureSeq INTEGER NOT NULL,
            rawInput TEXT NOT NULL,barcode TEXT NOT NULL,inputSource TEXT NOT NULL,confirmationPolicy TEXT NOT NULL,
            receivedWallMs INTEGER NOT NULL,receivedElapsedMs INTEGER NOT NULL,receivedBoot INTEGER NOT NULL,
            snapshotId TEXT NOT NULL,expectedDispatchCycle INTEGER,sourceRecoveryEpoch TEXT NOT NULL,
            authorizationId TEXT NOT NULL,assignmentEpoch TEXT NOT NULL,orderRevision INTEGER NOT NULL,inputRevision INTEGER NOT NULL,
            state TEXT NOT NULL,error TEXT NOT NULL,confirmedAt TEXT,failureAckAt TEXT,
            FOREIGN KEY(assignmentId) REFERENCES work_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_work_capture_originDeviceId_deviceCaptureSeq ON work_capture(originDeviceId,deviceCaptureSeq)")
        db.execSQL("CREATE UNIQUE INDEX index_work_capture_orderId_orderScanSeq ON work_capture(orderId,orderScanSeq)")
        db.execSQL("CREATE INDEX index_work_capture_assignmentId_orderScanSeq ON work_capture(assignmentId,orderScanSeq)")
        db.execSQL("CREATE INDEX index_work_capture_assignmentId_state_orderScanSeq ON work_capture(assignmentId,state,orderScanSeq)")
    }

    private fun createScans(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE work_scan (captureId TEXT NOT NULL PRIMARY KEY,assignmentId TEXT NOT NULL,orderScanSeq INTEGER NOT NULL,
            barcode TEXT NOT NULL,baleId TEXT NOT NULL,deliveryCode TEXT NOT NULL,model TEXT NOT NULL,name TEXT NOT NULL,
            rawWeight TEXT NOT NULL,weightGrams INTEGER NOT NULL,expectedDispatchCycle INTEGER NOT NULL,savedAt TEXT NOT NULL,
            localSeq INTEGER NOT NULL,status TEXT NOT NULL,
            FOREIGN KEY(captureId) REFERENCES work_capture(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(assignmentId) REFERENCES work_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_work_scan_assignmentId_orderScanSeq ON work_scan(assignmentId,orderScanSeq)")
        db.execSQL("CREATE INDEX index_work_scan_assignmentId_status_orderScanSeq ON work_scan(assignmentId,status,orderScanSeq)")
        db.execSQL("""CREATE TABLE work_guard (barcode TEXT NOT NULL PRIMARY KEY,captureId TEXT NOT NULL,assignmentId TEXT NOT NULL,
            dispatchCycle INTEGER NOT NULL,FOREIGN KEY(captureId) REFERENCES work_scan(captureId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_work_guard_captureId ON work_guard(captureId)")
        db.execSQL("CREATE INDEX index_work_guard_assignmentId ON work_guard(assignmentId)")
    }

    private fun createEvents(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE work_event (id TEXT NOT NULL PRIMARY KEY,assignmentId TEXT NOT NULL,captureId TEXT,
            originDeviceId TEXT NOT NULL,operatorId TEXT NOT NULL,localSeq INTEGER NOT NULL,type TEXT NOT NULL,
            detail TEXT NOT NULL,wallMs INTEGER NOT NULL,elapsedMs INTEGER NOT NULL,boot INTEGER NOT NULL,
            FOREIGN KEY(assignmentId) REFERENCES work_order(assignmentId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(captureId) REFERENCES work_capture(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE UNIQUE INDEX index_work_event_originDeviceId_localSeq ON work_event(originDeviceId,localSeq)")
        db.execSQL("CREATE INDEX index_work_event_assignmentId_localSeq ON work_event(assignmentId,localSeq)")
        db.execSQL("CREATE INDEX index_work_event_captureId ON work_event(captureId)")
    }
}
