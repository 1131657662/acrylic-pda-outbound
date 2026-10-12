package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReconciliationInputTest {
    private ObjectNode command() {
        var result=SubmissionJson.object().put("commandVersion",1).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",UUID.randomUUID().toString()).put("dispatchId",UUID.randomUUID().toString())
            .put("commandType","RECONCILE_DISCOVER").put("transportRecoveryEpoch","1").put("reason","Physical verification");
        result.putObject("expected");return result;
    }
    @Test void acceptsOnlyTheBusinessFieldsForAValidDiscovery() {
        assertThatCode(()->ReconciliationInput.validate(command())).doesNotThrowAnyException();
    }
    @Test void rejectsCoercedProtocolVersionAndRecoveryEpochTypes() {
        var version=command().put("commandVersion","1");var epoch=command().put("transportRecoveryEpoch",1);
        assertThatThrownBy(()->ReconciliationInput.validate(version)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->ReconciliationInput.validate(epoch)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsExtraTargetIdentityAndNoncanonicalUuid() {
        var extra=command().put("caseId",UUID.randomUUID().toString());var uuid=command().put("dispatchId","1-1-1-1-1");
        assertThatThrownBy(()->ReconciliationInput.validate(extra)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->ReconciliationInput.validate(uuid)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsEvidenceOnAnApprovalCommand() {
        var body=command();body.remove("dispatchId");body.put("commandType","RECONCILE_APPROVE").put("caseId",UUID.randomUUID().toString());
        body.putObject("evidence");assertThatThrownBy(()->ReconciliationInput.validate(body)).isInstanceOf(BusinessException.class);
    }
    @Test void quarantineDiscoveryBindsBaleWithoutInventingADispatch() {
        var body=command(); body.remove("dispatchId"); body.put("commandType","RECONCILE_QUARANTINE_DISCOVER").put("baleId",UUID.randomUUID().toString());
        assertThatCode(()->ReconciliationInput.validate(body)).doesNotThrowAnyException();
        body.put("dispatchId",UUID.randomUUID().toString());
        assertThatThrownBy(()->ReconciliationInput.validate(body)).isInstanceOf(BusinessException.class);
    }
}
