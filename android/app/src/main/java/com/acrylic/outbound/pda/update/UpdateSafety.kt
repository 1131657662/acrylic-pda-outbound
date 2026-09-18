package com.acrylic.outbound.pda.update

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.work.WorkUi

class UpdateSafety(private val db: PdaDatabase, private val work: () -> WorkUi) {
    fun installBlockReason(): String? {
        val ui = work()
        if (ui.queued > 0 || ui.pending > 0 || ui.candidate != null) return "请先处理扫码输入或保存确认，再安装更新"
        if (ui.order?.state == "SCANNING") return "请先退出扫码作业，再安装更新"
        if (db.work().unresolvedCount() > 0) return "本机仍有未处理输入，请先恢复并处理"
        if (db.submissions().unknownCount() > 0) return "上次上传结果未核对，请先查询原批次"
        if (db.recalls().activeCount() > 0) return "当前正在办理召回或恢复，请完成后再安装"
        if (db.continuations().pendingCount() > 0) return "当前有待确认的续作资料，请完成核对后再安装"
        return null
    }
}
