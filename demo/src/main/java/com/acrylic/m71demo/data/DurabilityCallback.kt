package com.acrylic.m71demo.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class DurabilityCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        // FullSyncOpenHelper configures FULL on the pool, including replacement connections.
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS active_order_barcode ON local_scan(orderId,barcode) WHERE status='ACTIVE'")
        db.beginTransactionNonExclusive()
        try {
            db.query("PRAGMA synchronous").use {
                it.moveToFirst()
                check(it.getInt(0) == 2) { "实际写连接未启用FULL耐久，停止作业并诊断" }
            }
        } finally { db.endTransaction() }
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE UNIQUE INDEX active_order_barcode
            ON local_scan(orderId,barcode) WHERE status='ACTIVE'""")
        db.execSQL("""CREATE TRIGGER positive_plan BEFORE INSERT ON local_order_line
            WHEN NEW.plannedCount<=0 BEGIN SELECT RAISE(ABORT,'positive plan required'); END""")
        db.execSQL("""CREATE TRIGGER positive_weight BEFORE INSERT ON local_inventory_bale
            WHEN NEW.weightGrams<=0 BEGIN SELECT RAISE(ABORT,'positive weight required'); END""")
        db.execSQL("""CREATE TRIGGER require_scan_cycle BEFORE INSERT ON local_scan
            WHEN NEW.expectedDispatchCycle IS NULL OR NEW.expectedDispatchCycle<=0
            BEGIN SELECT RAISE(ABORT,'dispatch cycle required'); END""")
    }
}
