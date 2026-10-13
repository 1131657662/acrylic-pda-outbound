package com.acrylic.outbound.pda.independent

internal class IndependentScanValidation(private val store: IndependentScanStore) {
    fun reason(capture: IndependentCapture): String? {
        val task = store.check(capture.taskId); store.requireNoOtherInput(task.id)
        check(store.db.independentTasks().state(task.id)?.state == "SCANNING")
        check(capture.deviceId == task.deviceId)
        store.verifyTotals(task.id)
        if (!capture.rawInput.trim(' ', '\r', '\n', '\t').matches(Regex("[A-Za-z0-9]{11}")) ||
            !capture.barcode.matches(Regex("[A-Z0-9]{11}"))) return "条码必须是完整11位数字或英文字母"
        val source = store.row(capture.barcode) ?: return "条码不在本任务固定原sku资料中"
        val bale = checkNotNull(source.parsedFields)
        val line = store.db.independentTasks().lines(task.id).singleOrNull { it.deliveryCode == bale.deliveryCode }
            ?: return "产品不属于本车固定计划"
        check(line.model == bale.model && line.name == bale.name) { "原资料与产品计划映射不一致，请停扫保全" }
        if (store.db.scans().guard(bale.barcode) != null || store.db.temporaryScans().guard(bale.barcode) != null ||
            store.db.temporaryHandoverGuards().get(bale.barcode) != null || store.dao.guard(bale.barcode) != null)
            return "该条码已在本机其他有效记录中保存，不能重复装车"
        val cloud = store.db.barcodes().active(task.recoveryEpoch, task.warehouseId, bale.barcode)
        if (cloud != null && cloud.status != "AVAILABLE") return "已有云端占用、隔离或出库事实，请交主管核对"
        val actual = store.dao.products(task.id).singleOrNull { it.deliveryCode == bale.deliveryCode }?.count ?: 0
        if (actual >= line.plannedCount || store.dao.total(task.id).count >= task.plannedTotal) return "固定计划已满，禁止超装"
        return null
    }
}
