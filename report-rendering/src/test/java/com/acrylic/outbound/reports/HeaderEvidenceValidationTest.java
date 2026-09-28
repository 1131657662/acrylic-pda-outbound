package com.acrylic.outbound.reports;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderEvidenceValidationTest {
    private Adjustment header(String field,String before,String after) {
        return new Adjustment("header-doc","",2,Adjustment.Kind.HEADER_CORRECTION,"",0,0,"0","0",field,before,after,
                Samples.CONFIRMED.plusSeconds(60),"抬头更正","approver","create_receipt:actual-test-receipt / headerPatch:/"+field);
    }
    private OrderSnapshot order(List<Adjustment> evidence) {
        var o=Samples.order(1);
        return new OrderSnapshot(o.id(),o.number(),o.revision(),2,o.source(),o.originalHeader(),o.effectiveHeader(),o.confirmedAt(),
                o.sequenceProof(),o.products(),o.originalLines(),o.effectiveLines(),evidence,o.exceptions());
    }
    @Test void acceptsDocumentEvidenceWithoutInventingNumericLineIds() throws Exception {
        var o=order(List.of(header("orderNo","TEST-R08-001","HEADER-002")));
        var snapshot=Samples.report(List.of(o));assertDoesNotThrow(()->SnapshotValidator.validate(snapshot));
        assertEquals("TEST-R08-001",o.number());assertEquals("HEADER-002",Values.displayNumber(o));
        assertTrue(PdfHtml.document(snapshot).contains("<h2>订单 HEADER-002</h2>"));
        assertTrue(PdfHtml.document(snapshot).contains("原单号：TEST-R08-001"));
    }
    @Test void rejectsHeaderWhoseBeforeValueDiffersFromOriginal() {
        var snapshot=Samples.report(List.of(order(List.of(header("orderNo","WRONG","HEADER-002")))));
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(snapshot));
        assertTrue(error.getMessage().contains("抬头调整前值不符"));
    }
    @Test void rejectsDuplicateFieldEvidenceInTheSameVersion() {
        var a=header("orderNo","TEST-R08-001","HEADER-002");
        assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(Samples.report(List.of(order(List.of(a,a))))));
    }
    @Test void doesNotRelaxThePhysicalLineIdRequirement() {
        var a=new Adjustment("physical","",2,Adjustment.Kind.WEIGHT_CORRECTION,"dispatch-1",1,1,"300501","300500",
                "","","",Samples.CONFIRMED.plusSeconds(60),"重量","approver","physical-proof");
        var error=assertThrows(IllegalArgumentException.class,()->SnapshotValidator.validate(Samples.report(List.of(order(List.of(a))))));
        assertTrue(error.getMessage().contains("必填文本为空"));
    }
    @Test void originalConfirmationKeepsItsDisplayNumber() {
        var original=Samples.order(1);assertEquals(original.number(),Values.displayNumber(original));
    }
}
