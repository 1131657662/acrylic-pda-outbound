package com.acrylic.m71demo.data

import androidx.room.*

/** Every joined key is unique. Missing optional rows remain null for the existing rejection rules. */
@Dao
interface ValidationDao {
    @Query("""SELECT o.id AS o_id, o.vehicle AS o_vehicle, o.snapshotId AS o_snapshotId, o.authorizationId AS o_authorizationId, o.source AS o_source, o.state AS o_state, o.nextOrderScanSeq AS o_nextOrderScanSeq, o.actualCount AS o_actualCount, o.weightGrams AS o_weightGrams, o.createdAt AS o_createdAt, o.recoveryEpoch AS o_recoveryEpoch, o.workRound AS o_workRound,
            l.orderId AS l_orderId, l.deliveryCode AS l_deliveryCode, l.plannedCount AS l_plannedCount, l.actualCount AS l_actualCount, l.weightGrams AS l_weightGrams,
            b.snapshotId AS b_snapshotId, b.barcode AS b_barcode, b.deliveryCode AS b_deliveryCode, b.model AS b_model, b.name AS b_name, b.weightGrams AS b_weightGrams, b.rawWeight AS b_rawWeight,
            c.barcode AS c_barcode, c.dispatchCycle AS c_dispatchCycle, c.recoveryEpoch AS c_recoveryEpoch, c.state AS c_state,
            g.barcode AS g_barcode, g.orderId AS g_orderId, g.scanId AS g_scanId, g.dispatchCycle AS g_dispatchCycle, g.state AS g_state,
            a.id AS a_id, a.deviceId AS a_deviceId, a.issuedAt AS a_issuedAt, a.expiresAt AS a_expiresAt, a.bootSessionId AS a_bootSessionId, a.elapsedExpiresNs AS a_elapsedExpiresNs, a.signature AS a_signature,
            (SELECT COALESCE(SUM(plannedCount), 0) FROM local_order_line WHERE orderId=o.id) AS plannedTotal
        FROM local_order o
        LEFT JOIN local_order_line l ON l.orderId=o.id AND l.deliveryCode=substr(:barcode,1,3)
        LEFT JOIN local_inventory_bale b ON b.snapshotId=:snapshot AND b.barcode=:barcode
        LEFT JOIN local_cloud_bale_state c ON c.barcode=:barcode
        LEFT JOIN local_barcode_guard g ON g.barcode=:barcode
        LEFT JOIN local_user_auth a ON a.id=:authorization
        WHERE o.id=:orderId""")
    fun context(orderId: String, snapshot: String, barcode: String, authorization: String): ValidationRows?
}

data class ValidationRows(
    @Embedded(prefix = "o_") val order: DemoOrder,
    @Embedded(prefix = "l_") val line: OrderLine?,
    @Embedded(prefix = "b_") val bale: InventoryBale?,
    @Embedded(prefix = "c_") val cloud: CloudBaleState?,
    @Embedded(prefix = "g_") val guard: BarcodeGuard?,
    @Embedded(prefix = "a_") val auth: DemoAuthorization?,
    val plannedTotal: Long,
)
