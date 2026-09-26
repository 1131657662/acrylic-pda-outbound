package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.evidence.LocalDraftQueue

object LocalDraftEvidenceNotice {
    fun text(stopped: Boolean, queue: LocalDraftQueue?): String {
        val detail = when {
            !stopped -> "适用于需要保留资料待核对的本机草稿。零包、未处理输入均保留；停止后本单不能继续扫码或修改。"
            queue == null -> "停止签名和原件已保存在本机，尚未安排补传。"
            queue.state == "ORIGINALS_STORED" -> "服务器原件保存回执已核验；业务事实仍未确认，不代表业务提交成功。"
            queue.state == "ACTION_REQUIRED" -> "补传待处理：${queue.error}\n原请求和已收到的响应保留，请核对后重试原操作。"
            else -> "原请求已保存，Wi-Fi可用时继续核对；已核验 ${queue.verifiedRequests}/${queue.totalRequests} 个原请求。${if (queue.error.isNotBlank()) "\n${queue.error}" else ""}"
        }
        return "$detail\n原准备、扫描、未处理输入和实物占用持续保留，不恢复扫码、不放行、不删除。"
    }
}
