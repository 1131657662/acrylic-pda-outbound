package com.acrylic.m71demo.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DemoMigrations {
    val from2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS active_order_barcode")
            db.execSQL("ALTER TABLE local_input_capture ADD COLUMN confirmationPolicy TEXT NOT NULL DEFAULT 'MANUAL'")
            db.execSQL("ALTER TABLE local_scan ADD COLUMN confirmationPolicy TEXT NOT NULL DEFAULT 'MANUAL'")
        }
    }

    // Preserve the earlier validation APK's data when adding multi-origin audit pagination.
    val from1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Room cannot declare a partial index; recreate it in onOpen after schema validation.
            db.execSQL("DROP INDEX IF EXISTS active_order_barcode")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_local_operation_event_orderId_originDeviceId_localSeq ON local_operation_event(orderId,originDeviceId,localSeq)")
        }
    }
}
