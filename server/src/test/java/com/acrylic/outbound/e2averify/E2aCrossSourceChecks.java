package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeBusinessFacts;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageObjects;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

final class E2aCrossSourceChecks {
    private E2aCrossSourceChecks() { }

    static void verify(E2aCrossSourceFixture fixture) {
        var facts = new SourceEvidenceIntakeBusinessFacts(fixture.jdbc, fixture.files, org.mockito.Mockito.mock(com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeLocalPayload.class), null, new com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePayload(null,null));
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(fixture.jdbc.getDataSource()));
        var binding = fixture.binding(UUID.randomUUID());
        var accepted = transactions.write(s -> facts.require(binding, fixture.items(), 2));
        assertThat(accepted).containsExactly(E2aCrossSourceFixture.item("SCAN", fixture.ownScan));
        rejectMissing(fixture, facts, transactions);
        rejectCrossWarehouse(fixture, facts, transactions);
        var forged = fixture.earlier.deepCopy().put("state", "ABORTED");
        var forgedItems = List.of(E2aCrossSourceFixture.item("CAPTURE", forged), fixture.items().get(1),
            fixture.items().get(2), fixture.items().get(3));
        rejected("E2A_OTHER_CAPTURE_NOT_STORED", () -> transactions.write(s -> facts.require(binding, forgedItems, 2)));
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id IN (?,?)", Integer.class,
            Ids.bytes(fixture.ownBale), Ids.bytes(fixture.priorBale))).isZero();
    }

    private static void rejectMissing(E2aCrossSourceFixture f, SourceEvidenceIntakeBusinessFacts facts,
        SourceEvidenceIntakeTransactions transactions) {
        transactions.write(status -> {
            f.jdbc.update("DELETE FROM submitted_capture WHERE id=?", Ids.bytes(SubmissionJson.uuid(f.earlier, "id")));
            rejected("E2A_OTHER_CAPTURE_NOT_STORED", () -> facts.require(f.binding(UUID.randomUUID()), f.items(), 2));
            status.setRollbackOnly(); return null;
        });
    }

    private static void rejectCrossWarehouse(E2aCrossSourceFixture f, SourceEvidenceIntakeBusinessFacts facts,
        SourceEvidenceIntakeTransactions transactions) {
        var foreign = new SourceEvidenceIntakePageObjects.Binding(UUID.randomUUID(), f.scope, f.submission, 1,
            f.uuid("deviceId"), f.uuid("operatorId"), UUID.randomUUID());
        rejected("E2A_OTHER_CAPTURE_NOT_STORED", () -> transactions.write(s -> facts.require(foreign, f.items(), 2)));
    }

    private static void rejected(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
