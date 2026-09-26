package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalE2aRawStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun multiMegabyteOriginalObjectSurvivesBoundedCursorReads() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            val frozen = TemporaryFreeze(f.store).freeze(f.id, "短装")
            val original = checkNotNull(f.db.localE2aObjects().frozen(f.id, frozen.id))
            // Exercise storage only; this padding is never accepted as frozen business evidence.
            val bytes = "中".repeat(800000).toByteArray(Charsets.UTF_8)
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_e2a_object SET rawBytes=?,bytesSha256=? WHERE objectId=?",
                arrayOf(bytes, E2aWire.sha(bytes), original.objectId))
            f.reopen()
            val restored = checkNotNull(f.db.localE2aObjects().byObject(f.id, original.objectId))
            assertArrayEquals(bytes, restored.rawBytes)
            assertArrayEquals(bytes, f.db.localE2aObjects().frozen(f.id, frozen.id)?.rawBytes)
            assertNull(f.db.localE2aObjects().byObject(java.util.UUID.randomUUID().toString(), original.objectId))
        }
    }
}
