package com.acrylic.outbound.pda.preparation

import org.json.JSONObject

/** Reject a claim preview unless its order and warehouse match the checked identity. */
internal object ClaimPreviewWarehouseGuard {
    fun requireMatches(preview: JSONObject, orderId: String, warehouseId: String) {
        check(preview.getString("status") == "READY" && preview.getString("id") == orderId &&
            preview.getString("warehouseId") == warehouseId) {
            "订单已变化或领取仓库不匹配，请刷新核对"
        }
    }
}
