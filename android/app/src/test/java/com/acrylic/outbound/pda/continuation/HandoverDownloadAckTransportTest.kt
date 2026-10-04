package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.network.PdaTransportFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
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
class HandoverDownloadAckTransportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun disconnectedAcknowledgementReplaysIdenticalBodyAfterDatabaseReopen() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            val original = f.server.dispatcher
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    original.dispatch(request)
                    return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                }
            }

            assertThrows(PdaTransportFailure::class.java) { f.ack() }
            assertEquals(1, f.ackBodies.size)
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
            f.local.reopen()
            f.server.dispatcher = original

            assertNotNull(f.ack().ackAt)
            assertEquals(2, f.server.requestCount)
            assertEquals(f.ackBodies[0], f.ackBodies[1])
            assertEquals("HANDOVER_FROZEN", f.local.db.work().order(f.local.target)?.state)
        }
    }

    @Test fun originalFactsChangedDuringRequestCannotBeLocallyAcknowledged() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.local.install()
            f.afterResponse = { f.local.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event") }

            assertThrows(IllegalStateException::class.java) { f.ack() }

            assertEquals(1, f.ackBodies.size)
            assertNull(f.local.db.preparations().order(f.local.target)?.ackAt)
            assertEquals("HANDOVER_FROZEN", f.local.db.work().order(f.local.target)?.state)
        }
    }
}
