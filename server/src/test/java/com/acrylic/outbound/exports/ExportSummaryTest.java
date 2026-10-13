package com.acrylic.outbound.exports;

import com.acrylic.outbound.reports.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExportSummaryTest {
    private static final Instant CONFIRMED=Instant.parse("2026-09-17T16:00:00Z");
    @Test void zeroNetKeepsOriginalOrderAndProductWithExactNegativeAdjustment() {
        var summary=ExportSummary.from(snapshot(0,"0"));
        assertThat(summary.actualCount()).isZero();assertThat(summary.weightGrams()).isEqualTo("0");
        assertThat(summary.originalWeightGrams()).isEqualTo("9007199254740993");
        assertThat(summary.adjustedCount()).isEqualTo(-1);
        assertThat(summary.adjustedWeightGrams()).isEqualTo("-9007199254740993");
        assertThat(summary.orderCount()).isEqualTo(1);
        assertThat(summary.orders().getFirst().resultVersion()).isEqualTo(2);
        assertThat(summary.orders().getFirst().originalDay()).isEqualTo("2026-09-18");
        assertThat(summary.products()).hasSize(1);
        assertThat(summary.products().getFirst().actualCount()).isZero();
    }
    @Test void weightCorrectionUsesIntegerGramsWithoutDoubleRounding() {
        var summary=ExportSummary.from(snapshot(1,"9007199254740994"));
        assertThat(summary.weightGrams()).isEqualTo("9007199254740994");
        assertThat(summary.adjustedWeightGrams()).isEqualTo("1");
        assertThat(summary.products().getFirst().weightGrams()).isEqualTo(summary.weightGrams());
    }
    @Test void missingNetLineRejectsInsteadOfPretendingZero() {
        var snapshot=snapshot(0,"0");var o=snapshot.orders().getFirst();
        var damaged=new OrderSnapshot(o.id(),o.number(),o.revision(),o.resultVersion(),o.source(),o.originalHeader(),o.effectiveHeader(),
                o.confirmedAt(),o.sequenceProof(),o.products(),o.originalLines(),List.of(),o.adjustments(),o.exceptions());
        assertThatThrownBy(()->ExportSummary.from(new ReportSnapshot(snapshot.metadata(),List.of(damaged))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("每条原事实");
    }
    private static ReportSnapshot snapshot(int count,String grams) {
        var line=new ReportLine("dispatch","capture",1,"device",1,"operator","PC_MANUAL","12345678901",1,"p", "9007199254740993",
                "9007199254740.993","","","DEVICE_UNVERIFIED","");
        var header=new OrderSnapshot.Header("vehicle","container","remark");
        var adjustment=new Adjustment("correction","line",2,count==0?Adjustment.Kind.PHYSICAL_RETURN:Adjustment.Kind.WEIGHT_CORRECTION,
                line.dispatchId(),1,count,line.weightGrams(),grams,"","","",CONFIRMED.plusSeconds(1),"reason","supervisor","evidence");
        var order=new OrderSnapshot("order","number",1,2,"PC",header,header,CONFIRMED,
                new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.PERSISTED_ORDER_SEQUENCE,"original sequence"),
                List.of(new OrderSnapshot.Product("p","name","model",1)),List.of(line),
                List.of(new OrderSnapshot.EffectiveLine(line.dispatchId(),count,grams)),List.of(adjustment),List.of());
        var day=LocalDate.parse("2026-09-18");var cutoff=CONFIRMED.plusSeconds(2);
        return new ReportSnapshot(new ReportSnapshot.Metadata("snapshot","fixed",cutoff,cutoff,day,day,ReportSnapshot.Basis.CURRENT_EFFECTIVE,
                ReportSnapshot.Provenance.TEST_SAMPLE,"synthetic unit fixture","original sequence"),List.of(order));
    }
}
