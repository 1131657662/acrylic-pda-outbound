package com.acrylic.outbound.pda.display

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.sync.SyncDecision
import com.acrylic.outbound.pda.sync.SyncScheduleState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = Application::class)
class SyncDisplayTimeTest {
    @Test fun persistedSyncMessageUsesShanghaiTimeWithoutChangingRetryState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = SyncScheduleState(context)
        val now = Instant.parse("2026-09-20T04:37:05.999Z").toEpochMilli()
        state.save("display-test", SyncDecision("暂未同步完成", 30), now, 2)
        val before = context.getSharedPreferences("background-sync", Context.MODE_PRIVATE).all.toMap()

        val display = SyncScheduleState(context).message()

        assertEquals("自动同步：暂未同步完成\n最近核对：2026-09-20 12:37:05", display)
        assertEquals(before, context.getSharedPreferences("background-sync", Context.MODE_PRIVATE).all)
        assertFalse(state.ready("display-test", now + 29_999))
        assertTrue(state.ready("display-test", now + 30_000))
        assertEquals(2, state.attempt("display-test"))
    }

    @Test fun noSyncRecordDoesNotDisplayTheCurrentTime() {
        val state = SyncScheduleState(ApplicationProvider.getApplicationContext())
        state.reset()
        assertEquals("自动同步：连接 Wi-Fi 且空闲时自动同步；原记录保留", state.message())
    }
}
