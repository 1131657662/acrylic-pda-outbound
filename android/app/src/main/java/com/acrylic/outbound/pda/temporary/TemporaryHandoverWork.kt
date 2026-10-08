package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.update.UpdateGate
import java.util.concurrent.Callable

/** Opens a verified TEMP handover round; production builds never expose this transition. */
class TemporaryHandoverWork(private val store: TemporaryWorkStore,
    private val development: Boolean = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) {

    @Synchronized
    fun start(id: String): TemporaryDraft = UpdateGate.business {
        enabled()
        val loaded = store.load(id)
        check(store.handoverDevelopment) { "临时交接作业开发能力未启用" }
        check(loaded.workRound > 1 && loaded.state in setOf("HANDOVER_BLOCKED", "SCANNING")) {
            "交接新轮状态不可开启"
        }
        val baseline = TemporaryHandoverBaseline(store).read(loaded, true)
        store.db.runInTransaction(Callable {
            val current = checkNotNull(store.db.temporaryDrafts().get(id))
            check(current == loaded) { "交接草稿已变化，请重新核对" }
            if (current.state == "SCANNING") {
                TemporaryHandoverBaseline(store).read(current, true)
                return@Callable current
            }
            check(current.state == "HANDOVER_BLOCKED")
            store.requireNoOtherInput(id)
            check(baseline.stage.toWorkRound == current.workRound)
            TemporaryHandoverBaseline(store).read(current, true)
            val opened = current.copy(state = "SCANNING")
            store.db.temporaryDrafts().update(opened)
            store.event(opened, null, "TEMP_HANDOVER_WORK_STARTED", baseline.stage.caseId)
            store.check(id)
            opened
        })
    }

    private fun enabled() {
        check(BuildConfig.DEBUG && development) { "临时交接开轮开发入口未启用，原资料保留" }
    }
}
