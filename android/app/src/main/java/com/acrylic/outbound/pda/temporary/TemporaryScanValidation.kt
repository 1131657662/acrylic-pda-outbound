package com.acrylic.outbound.pda.temporary

class TemporaryScanValidation(private val store: TemporaryWorkStore) {
    fun reason(capture: TemporaryCapture): String? {
        val draft = store.check(capture.clientOrderId); val catalog = store.source(draft.clientOrderId)
        store.requireNoOtherInput(draft.clientOrderId)
        val identity = catalog.manifest.getJSONObject("identity")
        check(store.canScan(draft) && draft.revision == capture.orderRevision &&
            draft.authorizationId == capture.authorizationId && draft.creatorDeviceId == capture.originDeviceId &&
            draft.operatorId == capture.operatorId && capture.authorizationEpoch == identity.getString("authorizationEpoch") &&
            capture.sourceRecoveryEpoch == identity.getString("serverRecoveryEpoch") &&
            capture.snapshotId == catalog.manifest.getString("inventorySnapshotId")) { "原临时扫码上下文已变化，请保留原记录" }
        if (!capture.rawInput.trim(' ', '\r', '\n', '\t').matches(Regex("[A-Za-z0-9]{11}")) ||
            !capture.barcode.matches(Regex("[A-Z0-9]{11}"))) return "条码必须是完整11位数字或英文字母"
        val bale = catalog.bales[capture.barcode] ?: return "条码不在原临时库存中"
        val line = store.db.temporaryDrafts().line(draft.clientOrderId, bale.code) ?: return "产品不属于本车计划"
        if (line.model != bale.model || line.name != bale.name) return "固定库存与产品映射不一致，请联系主管核对"
        if (bale.cycle != capture.expectedDispatchCycle) return "原出库周期不一致，请保留记录"
        availability(capture, bale)?.let { return it }
        if (store.db.scans().guard(bale.barcode) != null || store.db.temporaryScans().guard(bale.barcode) != null ||
            store.db.temporaryHandoverGuards().get(bale.barcode) != null)
            return "该条码已在本机订单中保存，不能重复装车"
        val actual = store.products(draft.clientOrderId).firstOrNull { it.deliveryCode == bale.code }?.count ?: 0
        if (actual >= line.plannedCount) return "该产品已达计划，禁止超装"
        return null
    }
    private fun availability(capture: TemporaryCapture, bale: TemporaryBale): String? {
        val draft = checkNotNull(store.db.temporaryDrafts().get(capture.clientOrderId))
        val cloud = store.db.barcodes().active(capture.sourceRecoveryEpoch, draft.warehouseId, bale.barcode)
        if (cloud != null && cloud.baleId != bale.baleId) return "条码身份发生变化，请联系主管核对"
        if (cloud != null && cloud.rowVersion == bale.rowVersion && (cloud.dispatchCycle != bale.cycle || cloud.status != bale.status))
            return "同版本条码状态不一致，请联系主管核对"
        val newer = cloud?.takeIf { it.rowVersion > bale.rowVersion }
        if (newer != null && newer.dispatchCycle != bale.cycle) return "云端出库周期已变化，不能替换原扫码周期"
        return when (newer?.status ?: bale.status) {
            "AVAILABLE" -> null
            "DISPATCHED" -> "该包已在云端确认出库，不能再次装车"
            "QUARANTINED" -> "该包已隔离，请联系主管处理"
            "RESERVED" -> "该包已有云端占用，请核对所属订单"
            else -> "该包当前不可用，请联网核对库存状态"
        }
    }
}
