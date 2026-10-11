package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.temporary.TemporaryRecoveredHandoverRepository
import com.acrylic.outbound.pda.temporary.TemporarySession

/** Reading retained recovery evidence never offers ordinary TEMP confirmation or scanning. */
internal class RecoveredHandoverCardReader(private val app: PdaApplication) {
    fun read(id: String): HandoverCard {
        val enabled = BuildConfig.DEBUG && BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT
        val status = runCatching {
            TemporaryRecoveredHandoverRepository(app.database, TemporarySession(app.session)).read(id)
            "原始交接资料已核验并保存在本机；接收确认尚未开放，请保持停止并联系主管。"
        }.getOrElse { "${it.message ?: "恢复交接资料需核对"}；原资料保留，请保持停止。" }
        return HandoverCard(id, "", "恢复交接资料", status, "RECOVERED_READ", "核对本机原始资料", actionAvailable = enabled)
    }
}
