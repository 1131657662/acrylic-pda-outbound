package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject
import java.time.Instant

/** The handover binding never substitutes for the recipient's own downloaded TEMP authorization. */
internal class TemporaryHandoverQualification(private val db: PdaDatabase, private val connection: TemporaryConnection) {
    fun verify(qualification: JSONObject, body: JSONObject, expected: JSONObject) {
        val id = TemporaryVerifier.uuid(qualification.getString("authorizationId"))
        val prepared = checkNotNull(db.temporary().byAuthorization(id)) { "请先在本机完整准备接收人的临时资料" }
        val request = checkNotNull(db.temporary().request(prepared.requestKey))
        check(db.temporaryContinuations().get(request.key) == null) { "专用续作资格不能用于接收交接" }
        TemporaryAccess(db, connection).request(request, expected)
        val catalog = TemporaryCatalog.load(db, request)
        TemporaryAckVerifier.verify(prepared, request, JSONObject(checkNotNull(prepared.ackJson) { "请先确认临时资料下载" }))
        TemporaryVerifier.identity(catalog.manifest, expected)
        TemporaryVerifier.time(catalog.manifest, connection.now())
        check(qualification.getString("manifestSha256") == prepared.manifestSha256 &&
            qualification.getString("expiresAt") == catalog.manifest.getString("expiresAt")) { "交接资格与本机原资格不一致" }
        for (field in listOf("serverRecoveryEpoch", "userAuthVersion", "deviceVersion"))
            check(qualification.getString(field) == expected.getString(field)) { "交接资格身份版本已变化" }
        for (field in listOf("inventorySnapshotId", "policyVersion"))
            check(body.getString(field) == catalog.manifest.getString(field)) { "接收资料与原计划基线不同" }
        check(body.getString("sourceRecoveryEpoch") == expected.getString("serverRecoveryEpoch"))
        check(connection.now().isBefore(Instant.parse(qualification.getString("expiresAt")))) { "交接资格已到期，保留原资料" }
        TemporaryVerifier.positive(qualification.getString("mappingVersion"))
    }
}
