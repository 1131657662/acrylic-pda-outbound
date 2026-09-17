package com.acrylic.m71demo.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [InventorySnapshot::class, InventoryBale::class, CloudBaleState::class,
    DemoOrder::class, OrderLine::class, InputCapture::class, ScanRecord::class,
    BarcodeGuard::class, AuditRecord::class, DeviceState::class, DemoAuthorization::class,
    FrozenSubmission::class], version = 3, exportSchema = true)
abstract class DemoDatabase : RoomDatabase() {
    abstract fun inventory(): InventoryDao
    abstract fun orders(): OrderDao
    abstract fun captures(): CaptureDao
    abstract fun scans(): ScanDao
    abstract fun audit(): AuditDao
    abstract fun validation(): ValidationDao
}
