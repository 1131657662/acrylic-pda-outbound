package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import java.time.Instant

/** The signed receiving description binds a new target epoch while preserving the original source epoch. */
internal object TemporaryRecoveredHandoverQualification {
    const val PURPOSE = "RECOVERED_TEMP_HANDOVER_QUALIFICATION"
    fun bind(qualification: JSONObject, context: JSONObject) {
        for (field in listOf("caseId", "scopeId", "warehouseId")) check(qualification.getString(field) == context.getString(field))
        check(qualification.getJSONObject("source").getString("workRound") == context.getString("fromWorkRound"))
    }
    fun verify(qualification: JSONObject, expected: JSONObject, now: Instant) {
        fields(qualification, "purpose qualificationSha256 qualificationId caseId scopeId warehouseId recipient source control baseline expiresAt capabilities")
        check(qualification.getString("purpose") == PURPOSE)
        for (field in listOf("qualificationId", "caseId", "scopeId", "warehouseId")) TemporaryVerifier.uuid(qualification.getString(field))
        hash(qualification, "qualificationSha256")
        check(qualification.getString("warehouseId") == expected.getString("warehouseId"))
        val recipient = qualification.getJSONObject("recipient")
        fields(recipient, "deviceId userId keySha256 deviceVersion userAuthVersion")
        for (field in listOf("deviceId", "userId", "deviceVersion", "userAuthVersion")) check(recipient.getString(field) == expected.getString(field))
        hash(recipient, "keySha256"); source(qualification.getJSONObject("source"))
        val control = qualification.getJSONObject("control")
        fields(control, "installationId epochId controlRevision controlRecordSha256 transportRecoveryEpoch databaseSchema mysqlServerUuid")
        for (field in listOf("installationId", "epochId", "mysqlServerUuid")) TemporaryVerifier.uuid(control.getString(field))
        for (field in listOf("controlRevision", "transportRecoveryEpoch")) TemporaryVerifier.positive(control.getString(field))
        hash(control, "controlRecordSha256"); check(control.getString("databaseSchema").isNotBlank())
        check(control.getString("transportRecoveryEpoch") == expected.getString("serverRecoveryEpoch") &&
            qualification.getJSONObject("source").getString("sourceRecoveryEpoch") != expected.getString("serverRecoveryEpoch"))
        baseline(qualification.getJSONObject("baseline"))
        check(now.isBefore(Instant.parse(qualification.getString("expiresAt")))) { "限定接收资格已到期，保留原资料" }
        val capabilities = qualification.getJSONArray("capabilities")
        check(capabilities.length() == 3 && (0 until 3).map(capabilities::getString) == listOf("PREPARE", "DOWNLOAD", "CONFIRM"))
    }
    private fun source(value: JSONObject) {
        fields(value, "authorizationId clientOrderId submissionId workRound sourceRecoveryEpoch inputHighWatermark payloadSha256")
        for (field in listOf("authorizationId", "clientOrderId", "submissionId")) TemporaryVerifier.uuid(value.getString(field))
        for (field in listOf("workRound", "sourceRecoveryEpoch", "inputHighWatermark")) TemporaryVerifier.positive(value.getString(field))
        hash(value, "payloadSha256")
    }
    private fun baseline(value: JSONObject) {
        fields(value, "inventorySnapshotId inventoryVersion inventorySha256 mappingRevision policyId policyVersion")
        for (field in listOf("inventorySnapshotId", "policyId")) TemporaryVerifier.uuid(value.getString(field))
        for (field in listOf("inventoryVersion", "mappingRevision", "policyVersion")) TemporaryVerifier.positive(value.getString(field))
        hash(value, "inventorySha256")
    }
    fun hash(value: JSONObject, field: String) { check(value.getString(field).matches(Regex("[a-f0-9]{64}"))) }
    fun fields(value: JSONObject, names: String) { check(value.keys().asSequence().toSet() == names.split(' ').toSet()) }
}
