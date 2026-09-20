package com.acrylic.outbound.pda.temporary

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration; original formal work and TEMP signed parts are retained. */
object TemporaryWorkMigration : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE temporary_request ADD COLUMN previousKey TEXT REFERENCES temporary_request(`key`) ON UPDATE NO ACTION ON DELETE NO ACTION")
        db.execSQL("DROP INDEX index_temporary_request_userId_deviceId_warehouseId")
        db.execSQL("CREATE INDEX index_temporary_request_userId_deviceId_warehouseId ON temporary_request(userId,deviceId,warehouseId)")
        db.execSQL("CREATE UNIQUE INDEX index_temporary_request_previousKey ON temporary_request(previousKey)")
        draft(db)
        line(db)
        capture(db)
        scan(db)
        guard(db)
        event(db)
    }
    private fun draft(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_draft` (`clientOrderId` TEXT NOT NULL, `creatorDeviceId` TEXT NOT NULL, `operatorId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `authorizationId` TEXT NOT NULL, `temporaryNo` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `vehicleNo` TEXT NOT NULL, `containerNo` TEXT NOT NULL, `reason` TEXT NOT NULL, `revision` INTEGER NOT NULL, `state` TEXT NOT NULL, `nextScanSeq` INTEGER NOT NULL, `planSha256` TEXT NOT NULL, PRIMARY KEY(`clientOrderId`), FOREIGN KEY(`authorizationId`) REFERENCES `temporary_preparation`(`authorizationId`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_temporary_draft_authorizationId` ON `temporary_draft` (`authorizationId`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_temporary_draft_creatorDeviceId_operatorId_warehouseId_clientOrderId` ON `temporary_draft` (`creatorDeviceId`, `operatorId`, `warehouseId`, `clientOrderId`)""")
    }
    private fun line(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_line` (`clientOrderId` TEXT NOT NULL, `deliveryCode` TEXT NOT NULL, `productId` TEXT NOT NULL, `model` TEXT NOT NULL, `name` TEXT NOT NULL, `plannedCount` INTEGER NOT NULL, PRIMARY KEY(`clientOrderId`, `deliveryCode`), FOREIGN KEY(`clientOrderId`) REFERENCES `temporary_draft`(`clientOrderId`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
    }
    private fun capture(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_capture` (`id` TEXT NOT NULL, `clientOrderId` TEXT NOT NULL, `originDeviceId` TEXT NOT NULL, `operatorId` TEXT NOT NULL, `orderScanSeq` INTEGER NOT NULL, `deviceCaptureSeq` INTEGER NOT NULL, `rawInput` TEXT NOT NULL, `barcode` TEXT NOT NULL, `inputSource` TEXT NOT NULL, `receivedWallMs` INTEGER NOT NULL, `receivedElapsedMs` INTEGER NOT NULL, `receivedBoot` INTEGER NOT NULL, `authorizationId` TEXT NOT NULL, `authorizationEpoch` TEXT NOT NULL, `snapshotId` TEXT NOT NULL, `sourceRecoveryEpoch` TEXT NOT NULL, `orderRevision` INTEGER NOT NULL, `expectedDispatchCycle` INTEGER, `state` TEXT NOT NULL, `error` TEXT NOT NULL, `confirmedAt` TEXT, `failureAckAt` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`clientOrderId`) REFERENCES `temporary_draft`(`clientOrderId`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_temporary_capture_clientOrderId_orderScanSeq` ON `temporary_capture` (`clientOrderId`, `orderScanSeq`)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_temporary_capture_originDeviceId_deviceCaptureSeq` ON `temporary_capture` (`originDeviceId`, `deviceCaptureSeq`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_temporary_capture_clientOrderId_state_orderScanSeq` ON `temporary_capture` (`clientOrderId`, `state`, `orderScanSeq`)""")
    }
    private fun scan(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_scan` (`captureId` TEXT NOT NULL, `clientOrderId` TEXT NOT NULL, `orderScanSeq` INTEGER NOT NULL, `barcode` TEXT NOT NULL, `baleId` TEXT NOT NULL, `deliveryCode` TEXT NOT NULL, `model` TEXT NOT NULL, `name` TEXT NOT NULL, `rawWeight` TEXT NOT NULL, `weightGrams` INTEGER NOT NULL, `expectedDispatchCycle` INTEGER NOT NULL, `savedAt` TEXT NOT NULL, `localSeq` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`captureId`), FOREIGN KEY(`captureId`) REFERENCES `temporary_capture`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`clientOrderId`) REFERENCES `temporary_draft`(`clientOrderId`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_temporary_scan_clientOrderId_orderScanSeq` ON `temporary_scan` (`clientOrderId`, `orderScanSeq`)""")
    }
    private fun guard(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_guard` (`barcode` TEXT NOT NULL, `captureId` TEXT NOT NULL, PRIMARY KEY(`barcode`), FOREIGN KEY(`captureId`) REFERENCES `temporary_scan`(`captureId`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_temporary_guard_captureId` ON `temporary_guard` (`captureId`)""")
    }
    private fun event(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `temporary_event` (`id` TEXT NOT NULL, `clientOrderId` TEXT NOT NULL, `captureId` TEXT, `originDeviceId` TEXT NOT NULL, `operatorId` TEXT NOT NULL, `localSeq` INTEGER NOT NULL, `type` TEXT NOT NULL, `detail` TEXT NOT NULL, `wallMs` INTEGER NOT NULL, `elapsedMs` INTEGER NOT NULL, `boot` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`clientOrderId`) REFERENCES `temporary_draft`(`clientOrderId`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`captureId`) REFERENCES `temporary_capture`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_temporary_event_originDeviceId_localSeq` ON `temporary_event` (`originDeviceId`, `localSeq`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_temporary_event_clientOrderId_localSeq` ON `temporary_event` (`clientOrderId`, `localSeq`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_temporary_event_captureId` ON `temporary_event` (`captureId`)""")
    }
}
