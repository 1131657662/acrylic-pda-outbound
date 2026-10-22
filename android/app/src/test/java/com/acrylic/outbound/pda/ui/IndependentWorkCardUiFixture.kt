package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.independent.IndependentWorkRuntime
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** Original task-page card creation followed by the original scan Activity and serial engine. */
internal class IndependentWorkCardUiFixture(credentialSeconds: Long? = null) : AutoCloseable {
    val task = IndependentTaskCardUiFixture(credentialSeconds)
    val base get() = task.card.base
    val engine get() = IndependentWorkRuntime.shared(base.app)
    private val controller: org.robolectric.android.controller.ActivityController<IndependentWorkActivity>
    val activity get() = controller.get()
    val original get() = checkNotNull(task.model.state.value.task).task
    val status get() = listOf(R.id.workMessage, R.id.workBlocked).joinToString {
        activity.findViewById<TextView>(it).text.toString()
    }
    val pickerButton: Button get() {
        val id = activity.resources.getIdentifier("workAuthorizeCard", "id", activity.packageName)
        assertTrue("scan page must consume the original card without transcribing its secret", id != 0)
        return checkNotNull(activity.findViewById(id))
    }
    init {
        task.receive(task.begin())
        task.activity.findViewById<Button>(R.id.openIndependentWork).performClick()
        val intent = checkNotNull(Shadows.shadowOf(task.activity).nextStartedActivity)
        assertEquals(original.id, intent.getStringExtra("taskId"))
        controller = Robolectric.buildActivity(IndependentWorkActivity::class.java, intent).setup()
        await { engine.state.value.task?.task?.id == original.id && engine.state.value.queued == 0 }
        activity.findViewById<Button>(R.id.workDetailsToggle).performClick()
    }
    fun begin(): Intent {
        pickerButton.performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult) { status }.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        return picker
    }
    fun receive(picker: Intent, file: File? = task.card.card) {
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        await { engine.state.value.queued == 0 && pickerButton.isEnabled }
    }
    fun recreate() { controller.recreate(); await { engine.state.value.queued == 0 } }
    fun background() { controller.pause().stop() }
    fun foreground() { controller.start().resume(); await { engine.state.value.queued == 0 } }
    fun deliverWhileStopped(picker: Intent) {
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(task.card.card)))
    }
    fun await(condition: () -> Boolean) = task.await(condition)
    override fun close() {
        try { controller.pause().stop().destroy() } finally {
            engine.close()
            ReflectionHelpers.setStaticField(IndependentWorkRuntime::class.java, "engine", null)
            ReflectionHelpers.setStaticField(IndependentWorkRuntime::class.java, "owner", null)
            task.close()
        }
    }
}
