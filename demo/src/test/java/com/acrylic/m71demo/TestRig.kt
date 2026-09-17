package com.acrylic.m71demo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.*
import com.acrylic.m71demo.workflow.*

class TestRig(private val planned: Int = 2) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "test-${java.util.UUID.randomUUID()}.db"
    val clock = FakeClock()
    private val auth = AuthorizationService(clock, object : DemoSigner {
        override fun sign(claim: String) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(claim.toByteArray()).joinToString("") { "%02x".format(it) }
    })
    var db = open()
    var store = DemoStore(db, clock, auth)
    val capture get() = CaptureService(store)
    val confirmation get() = ConfirmationService(store)
    val correction get() = CorrectionService(store)

    init { seed() }

    private fun open() = Room.databaseBuilder(context, DemoDatabase::class.java, name)
        .openHelperFactory { FullSyncOpenHelper(it) }
        .allowMainThreadQueries().setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(DemoMigrations.from1To2, DemoMigrations.from2To3)
        .addCallback(DurabilityCallback()).build()

    private fun seed() = db.runInTransaction {
        db.audit().insertDevice(DeviceState("device-A"))
        val authorization = auth.issue("device-A")
        db.audit().insertAuthorization(authorization)
        db.inventory().insertSnapshot(InventorySnapshot("snapshot", "test", 3, "TEST"))
        val bales = listOf(bale("003A0000001"), bale("003A0000002"), bale("571A0000001"))
        db.inventory().insertBales(bales)
        db.inventory().insertStates(bales.map { CloudBaleState(it.barcode) })
        db.orders().insert(DemoOrder("order", "TEST", "snapshot", authorization.id, createdAt = clock.wallMs()))
        db.orders().insertLines(listOf(OrderLine("order", "003", planned), OrderLine("order", "571", 1)))
    }

    fun receive(barcode: String): InputCapture {
        clock.elapsed += 250_000_000
        val input = IncomingBarcode(java.util.UUID.randomUUID().toString(), "order", barcode,
            "TEST", clock.wallMs(), clock.elapsedNs(), clock.bootId())
        val row = capture.receive(input)
        return capture.validate(row.id)
    }

    fun save(barcode: String): InputCapture {
        val c = receive(barcode)
        confirmation.request(c.id, clock.elapsedNs())
        confirmation.save(c.id)
        return db.captures().get(c.id)!!
    }

    fun reopen() {
        db.close()
        db = open()
        store = DemoStore(db, clock, auth)
    }

    private fun bale(code: String) = InventoryBale("snapshot", code, code.take(3), "MODEL", "测试包", 300500, "300.5")
    override fun close() { db.close(); context.deleteDatabase(name) }
}

class FakeClock : AppClock {
    var wall = 1_789_430_000_000L
    var elapsed = 1_000_000_000L
    var boot = "test-boot"
    override fun wallMs() = wall
    override fun elapsedNs() = elapsed
    override fun bootId() = boot
}
