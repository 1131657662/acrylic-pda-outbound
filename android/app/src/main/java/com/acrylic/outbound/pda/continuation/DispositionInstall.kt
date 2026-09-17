package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.work.WorkStore
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*

class DispositionInstall(private val store: WorkStore) {
    /** Runs within the continuation activation transaction. Guards and original scans stay intact. */
    fun apply(root: String, body: JSONObject, response: JSONObject) {
        val old = store.db.dispositions().proofs(root); val proofs = DispositionRecords.rows(response)
        check(SubmissionJson.canonical(old) == SubmissionJson.canonical(DispositionRecords.rows(body))) { "本地历史处置与恢复正文不一致" }
        DispositionRecords.verifyExtension(body, proofs)
        for (i in old.length() until proofs.length()) {
            val row = proofs.getJSONObject(i); val scan = checkNotNull(store.db.scans().get(row.getString("captureId")))
            check(scan.assignmentId == root && scan.status == "ACTIVE")
            check(store.db.scans().guard(scan.barcode)?.captureId == scan.captureId) { "原包本地防重占用不完整" }
            store.db.dispositions().insert(LocalDisposition(scan.captureId, root, row.getString("approvedEvidenceId"),
                row.getString("action"), SubmissionJson.canonical(row), i))
        }
        val total = store.db.scans().total(root); check(store.db.work().totals(root, total.count, total.grams, null) == 1)
        val products = store.db.scans().products(root).associateBy { it.deliveryCode }
        for (line in store.db.work().lines(root)) {
            val value = products[line.deliveryCode]
            check(store.db.work().lineTotals(root, line.deliveryCode, value?.count ?: 0, value?.grams ?: 0) == 1)
        }
        store.verifyTotals(root)
    }
}
