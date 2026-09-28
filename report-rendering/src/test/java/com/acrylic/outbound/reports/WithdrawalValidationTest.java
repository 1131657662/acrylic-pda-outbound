package com.acrylic.outbound.reports;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawalValidationTest {
    private Adjustment forward() {
        return Samples.adjustment("c1",2,Adjustment.Kind.DISPATCH_CANCELLATION,"dispatch-1",0,"0");
    }
    private Adjustment withdrawal(long version,String restoredWeight) {
        return new Adjustment("c1","line1",version,Adjustment.Kind.CORRECTION_WITHDRAWAL,"dispatch-1",0,1,
                "0",restoredWeight,"","","",Instant.parse("2026-09-20T01:00:00Z"),"申请人确认撤销","原审批人","withdrawal_receipt:r1");
    }
    private ReportSnapshot snapshot(long version,List<Adjustment> adjustments) {
        var o=Samples.order(1);
        return Samples.report(List.of(new OrderSnapshot(o.id(),o.number(),o.revision(),version,o.source(),
                o.originalHeader(),o.effectiveHeader(),o.confirmedAt(),o.sequenceProof(),o.products(),o.originalLines(),
                o.effectiveLines(),adjustments,o.exceptions())));
    }
    @Test void acceptsCancellationFollowedByExactWithdrawal() {
        assertDoesNotThrow(()->SnapshotValidator.validate(snapshot(3,List.of(forward(),withdrawal(3,"300501")))));
    }
    @Test void rejectsWithdrawalWithoutItsOriginalAdjustment() {
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(snapshot(2,List.of(withdrawal(2,"300501")))));
        assertTrue(error.getMessage().contains("撤销缺少原调整"));
    }
    @Test void rejectsWithdrawalWhoseRestorationDiffersFromOriginal() {
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(snapshot(3,List.of(forward(),withdrawal(3,"300500")))));
        assertTrue(error.getMessage().contains("未完整反向"));
    }
    @Test void rejectsRepeatedWithdrawalEvenInAnotherVersion() {
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(snapshot(4,List.of(forward(),withdrawal(3,"300501"),withdrawal(4,"300501")))));
        assertTrue(error.getMessage().contains("重复撤销"));
    }
    @Test void stillRejectsForwardEvidenceRepeatedAcrossVersions() {
        var duplicate=Samples.adjustment("c1",3,Adjustment.Kind.DISPATCH_CANCELLATION,"dispatch-1",0,"0");
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(snapshot(3,List.of(forward(),duplicate))));
        assertTrue(error.getMessage().contains("原调整凭据跨版本重复"));
    }
}
