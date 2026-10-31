package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.*

/** Displays retained recovery evidence without exposing the cancelled receiver-confirmation flow. */
internal class RecoveredHandoverCardReader(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val development: Boolean = BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT) {
    fun read(id: String): HandoverCard = runCatching {
        check(BuildConfig.DEBUG && development) { "恢复接收确认尚未开放" }
        val stage = TemporaryRecoveredHandoverRepository(db, connection, development).read(id)
        val display = RecoveredHandoverConfirmationDisplay(db).read(stage)
        HandoverCard(id, "", "恢复交接资料", "${display.details}\n原资料已核验并保存在本机；当前仅允许只读核对，接收确认与激活已取消。",
            "RECOVERED_READ", "核对本机原始资料",
            "${display.details}\n交接编号 $id\n请逐包核对保留清单。当前仅允许只读核对，接收确认与激活已取消。\n${display.lines}")
    }.getOrElse {
        HandoverCard(id, "", "恢复交接资料", "${it.message ?: "恢复交接资料需核对"}；原资料保留，请保持停止。",
            "RECOVERED_READ", "核对本人资格与原资料", actionAvailable = false)
    }
}
