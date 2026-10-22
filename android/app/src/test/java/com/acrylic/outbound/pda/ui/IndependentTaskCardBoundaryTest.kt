package com.acrylic.outbound.pda.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.databinding.ActivityIndependentTaskBinding
import com.acrylic.outbound.pda.security.TrustedTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class, qualifiers = "mdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentTaskCardBoundaryTest {
    @Test fun everyCapturedIdentityFieldChangeRejectsBeforeCreatingRequest() {
        IndependentTaskCardUiFixture().use { f ->
            val original = f.card.base.vault.read().toString()
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId", "userAuthVersion", "deviceVersion")) {
                f.card.base.vault.save(JSONObject(original)); val picker = f.begin()
                f.card.base.vault.save(JSONObject(original).put(field, "synthetic-changed"))
                f.receive(picker); f.assertNoRequest(); assertTrue(field + ": " + f.status, f.status.contains("变化"))
            }
        }
    }
    @Test fun recoveryFenceAfterSelectionRejectsOriginalCardWithoutRequest() {
        IndependentTaskCardUiFixture().use { f ->
            val picker = f.begin()
            runBlocking(Dispatchers.IO) { f.card.base.app.database.sync().saveRecovery(
                LocalRecoveryControl(observedServerEpoch = "1", mode = "RECONCILING")) }
            f.receive(picker); f.assertNoRequest(); assertTrue(f.status.contains("未通过"))
        }
    }
    @Test fun trustedTimeExpiryAfterSelectionCannotCreateTask() {
        IndependentTaskCardUiFixture(60).use { f ->
            val picker = f.begin(); ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
            val actor = checkNotNull(f.card.base.vault.read())
            val now = TrustedTime.now(actor.getJSONObject("timeAnchor"), TrustedTime.sample(f.card.base.app))
            assertTrue(now >= f.card.base.authority.issued.plusSeconds(60))
            f.receive(picker); f.assertNoRequest(); assertTrue(f.status, f.status.contains("未通过"))
        }
    }
    @Test fun auditTailFailureRetainsOriginalRequestAndCardRetryUsesOriginalBytes() {
        IndependentTaskCardUiFixture().use { f ->
            val command = f.draft; val db = f.card.base.app.database
            runBlocking(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_card_tail BEFORE INSERT ON independent_task_event BEGIN SELECT RAISE(ABORT,'synthetic tail'); END") }
            f.receive(f.begin())
            val original = runBlocking(Dispatchers.IO) {
                assertNull(db.independentTasks().task(command.id)); assertTrue(db.independentTasks().events(command.id).isEmpty())
                checkNotNull(db.independentTasks().request(command.id))
            }
            assertTrue(f.model.state.value.locked); f.recreate()
            runBlocking(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_card_tail") }
            f.receive(f.begin()); assertEquals(command.id, f.model.state.value.task?.task?.id)
            runBlocking(Dispatchers.IO) {
                assertEquals(original, db.independentTasks().request(command.id))
                assertEquals(1, db.independentTasks().events(command.id).size)
            }
        }
    }
    @Test fun cardActionWrapsAndRemainsReachableAtAllFourFontScales() {
        IndependentTaskCardUiFixture().use { f ->
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                val configuration = Configuration(f.activity.resources.configuration).apply { fontScale = scale }
                val context = f.activity.createConfigurationContext(configuration)
                val b = ActivityIndependentTaskBinding.inflate(LayoutInflater.from(ContextThemeWrapper(context, R.style.AppTheme)))
                f.activity.setContentView(b.root)
                b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY)); b.root.layout(0, 0, 320, 509)
                val action = b.createWithCard
                assertTrue(action.height >= 52); assertUnclipped(action)
                val rect = Rect(0, 0, action.width, action.height); b.root.offsetDescendantRectToMyCoords(action, rect)
                b.root.scrollTo(0, rect.top)
                assertTrue(action.isShown); assertTrue(rect.bottom - b.root.scrollY <= 509)
                assertEquals(scale, action.resources.configuration.fontScale)
                println("task-card scale=" + scale + " height=" + action.height + " lines=" + action.lineCount + " scroll=" + b.root.scrollY)
            }
        }
    }
    private fun assertUnclipped(view: TextView) {
        val layout = checkNotNull(view.layout)
        assertTrue(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        (0 until layout.lineCount).forEach {
            assertEquals(0, layout.getEllipsisCount(it)); assertTrue(layout.getLineRight(it) <= width + 0.5f)
        }
    }
}
