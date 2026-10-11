package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.PdaApplication

/** Reads original local task facts for the home screen without changing qualifications. */
class PreparationCards(private val app: PdaApplication) {
    fun formal(history: Boolean, offset: Int, limit: Int): List<LocalCard> {
        val saved = app.session.saved()
        return app.repository.localPage(history, offset, limit).map {
            val recalled = app.database.recalls().header(it.assignmentId) != null
            val continuation = app.database.continuations().current(it.assignmentId)
            val current = if (continuation == null) it else it.copy(assignmentJson = continuation.authorizationJson,
                authorizationHistoryJson = continuation.authorizationHistoryJson)
            val work = app.database.work().order(it.assignmentId)
            val readiness = if (work?.state == "HANDOVER_FROZEN") "旧冻结原件保留，只可核对；请由后台撤回重发" else app.repository.readiness(current)
            LocalCard(current, if (recalled) "收到召回 · 停止作业，须核对恢复证据" else readiness, recalled,
                work?.state, app.database.submissions().currentState(it.assignmentId),
                saved?.optString("userId") == it.operatorId && saved.optString("deviceId") == it.deviceId, work?.actualCount ?: 0)
        }
    }
    fun temporary(): List<TemporaryHomeCard> {
        val auth = app.session.saved() ?: return emptyList()
        return app.database.temporaryDrafts().page(auth.getString("deviceId"), auth.getString("userId"), auth.getString("warehouseId"), "")
            .take(20).sortedBy { if (it.state == "SCANNING") 0 else 1 }.map { TemporaryHomeCard(it, app.database.temporaryScans().total(it.clientOrderId).count,
                app.database.temporaryDrafts().lines(it.clientOrderId).sumOf { line -> line.plannedCount }) }
    }
}
