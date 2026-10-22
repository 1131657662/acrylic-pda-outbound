package com.acrylic.outbound.pda.ui

import android.widget.Button
import android.widget.EditText
import com.acrylic.outbound.pda.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentTaskCardTest {
    @Test fun originalCardCreatesTaskWithoutTranscribingPassword() {
        IndependentTaskCardUiFixture().use { f ->
            val command = f.draft; val original = f.card.card.readBytes()
            assertEquals("", f.activity.findViewById<EditText>(R.id.password).text.toString())
            val picker = f.begin()
            assertFalse(f.activity.findViewById<Button>(R.id.create).isEnabled)
            f.receive(picker)
            val task = checkNotNull(f.model.state.value.task) { f.status }.task
            assertEquals(command.id, task.id); assertEquals(command.sha(), task.commandSha256)
            assertEquals(2L, task.plannedTotal)
            assertFalse(f.pickerButton.isEnabled)
            assertArrayEquals(original, f.card.card.readBytes())
            assertEquals(1, f.card.base.server.requestCount)
            runBlocking(Dispatchers.IO) {
                val dao = f.card.base.app.database.independentTasks()
                assertEquals(1, dao.events(task.id).size)
                assertEquals("CREATE_PLANNED", dao.events(task.id).single().type)
            }
        }
    }
    @Test fun cancellationCreatesNoDurableRequest() {
        IndependentTaskCardUiFixture().use { f ->
            f.receive(f.begin(), null); f.assertNoRequest()
            assertTrue(f.status.contains("取消")); assertTrue(f.pickerButton.isEnabled)
        }
    }
    @Test fun changedDraftCannotApplyPreviouslySelectedCard() {
        IndependentTaskCardUiFixture().use { f ->
            val old = f.draft; val picker = f.begin()
            f.model.fields("另一合成车", old.reason)
            f.receive(picker); f.assertNoRequest(old.id)
            assertTrue(f.status.contains("变化"))
        }
    }
    @Test fun switchedLoginGenerationRejectsOriginalPickerResult() {
        IndependentTaskCardUiFixture().use { f ->
            val picker = f.begin()
            f.card.base.vault.save(JSONObject(f.card.base.authority.session.toString()).put("localSessionId", "synthetic-other-login"))
            f.receive(picker); f.assertNoRequest(); assertTrue(f.status.contains("变化"))
        }
    }
    @Test fun recreatedPageDiscardsPendingFileResult() {
        IndependentTaskCardUiFixture().use { f ->
            val picker = f.begin(); f.recreate(); f.receive(picker)
            f.assertNoRequest(); assertTrue(f.pickerButton.isEnabled)
        }
    }
    @Test fun revokedCredentialCannotBeRestoredByTaskCard() {
        IndependentTaskCardUiFixture().use { f ->
            f.card.base.credentials.revoke(); f.receive(f.begin()); f.assertNoRequest()
            assertTrue(f.status.contains("未通过")); assertTrue(f.card.base.credentialFile.exists())
        }
    }
    @Test fun forgedCardRetainsOriginalPersistentAttemptLimit() {
        IndependentTaskCardUiFixture().use { f ->
            val original = f.card.card.readText()
            f.card.card.writeText(JSONObject(original).put("credential", "x".repeat(32)).toString())
            repeat(5) { f.receive(f.begin()); f.assertNoRequest() }
            f.card.card.writeText(original); f.receive(f.begin()); f.assertNoRequest()
            assertTrue(f.status.contains("未通过")); assertEquals(1, f.card.base.server.requestCount)
        }
    }
    @Test fun logoutBeforeFileChoiceCannotLaunchOrCreate() {
        IndependentTaskCardUiFixture().use { f ->
            f.card.base.vault.clear(); f.pickerButton.performClick()
            assertNull(Shadows.shadowOf(f.activity).nextStartedActivityForResult)
            f.assertNoRequest(); assertTrue(f.status.contains("核对"))
        }
    }
}
