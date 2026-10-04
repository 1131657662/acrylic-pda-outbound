package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.recall.RecoveryFreeze
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverRecoveryProducerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun sourceGeneratedRecoveryPassesReceiverFactAndAuditChecks() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { fixture ->
            fixture.scan()
            fixture.scan("bad")
            fixture.db.runInTransaction {
                fixture.db.recalls().insert(LocalRecall(UUID.randomUUID().toString(), fixture.assignment, "{}"))
                fixture.db.work().recall(fixture.assignment)
            }
            val freeze = RecoveryFreeze(fixture.store) { fixture.now }
            val payload = checkNotNull(freeze.freeze(fixture.assignment,
                freeze.fingerprint(freeze.unresolved(fixture.assignment))).payload)
            val source = JSONObject().put("assignmentId", fixture.assignment)
                .put("deviceId", fixture.session.getString("deviceId"))
                .put("operatorId", fixture.session.getString("userId"))
            val manifest = JSONObject().put("stopReceipt", JSONObject().put("source", source))
            val body = JSONObject(payload)
            val stage = LocalHandoverStage(UUID.randomUUID().toString(), fixture.assignment,
                body.getString("orderId"), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                fixture.session.getString("warehouseId"), body.getString("captureHighWatermark").toLong(),
                "", manifest.toString(), "", payload.toByteArray(Charsets.UTF_8))

            HandoverRecoveryFacts.verify(stage)
            HandoverRecoveryAudit.verify(stage)
        }
    }
}
