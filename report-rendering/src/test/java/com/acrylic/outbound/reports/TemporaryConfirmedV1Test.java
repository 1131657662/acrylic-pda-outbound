package com.acrylic.outbound.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.acrylic.outbound.reports.ConfirmedV1Fixture.*;

class TemporaryConfirmedV1Test {
    private static final String DEVICE="b379d876-07e4-4ea3-a62b-09882cfc9981";
    private static final String CLIENT="9c151148-55ed-40e7-8700-5f5319f480cf";
    private ConfirmedV1Input.Order temporary() {
        var formal=order(ConfirmedV1Input.Channel.PDA);var receipt=(ObjectNode)formal.successReceipt();
        receipt.putObject("source").put("kind","TEMP").put("creatorDeviceId",DEVICE).put("clientOrderId",CLIENT);
        receipt.put("freezeDeviceId",DEVICE);
        var dispatches=formal.dispatches().stream().map(d->new ConfirmedV1Input.Dispatch(d.orderId(),d.submissionId(),
                Samples.changed(d.line(),"device",DEVICE))).toList();
        return new ConfirmedV1Input.Order(formal.id(),formal.number(),formal.revision(),formal.header(),formal.confirmedAt(),
                ConfirmedV1Input.Channel.TEMP_PDA,"original TEMP:"+DEVICE+":"+CLIENT,formal.products(),dispatches,
                formal.result(),receipt,formal.exceptions());
    }
    @Test void retainsTemporarySourceAndOriginalSequenceIncludingGaps() {
        var actual=ConfirmedV1Adapter.adapt(input(temporary())).orders().getFirst();
        assertEquals("TEMP_PDA",actual.source());
        assertEquals(List.of(1L,3L,5L),actual.originalLines().stream().map(ReportLine::orderScanSeq).toList());
        assertEquals(List.of(101L,103L,105L),actual.originalLines().stream().map(ReportLine::deviceCaptureSeq).toList());
        assertTrue(actual.sequenceProof().reference().contains(CLIENT));
    }
    @ParameterizedTest @ValueSource(strings={"formal","missing-client","extra-order","other-device","round-two"})
    void rejectsUnclosedTemporaryIdentity(String problem) {
        var original=temporary();var receipt=(ObjectNode)original.successReceipt();var source=(ObjectNode)receipt.path("source");
        switch(problem) {
            case "formal" -> source.put("kind","FORMAL");
            case "missing-client" -> source.remove("clientOrderId");
            case "extra-order" -> source.put("sourceOrderId",original.id());
            case "other-device" -> source.put("creatorDeviceId",CLIENT);
            case "round-two" -> receipt.put("workRound","2");
        }
        assertThrows(IllegalArgumentException.class,()->ConfirmedV1Adapter.adapt(input(withReceipt(original,receipt))));
    }
    @Test void temporaryReceiptCannotBePassedThroughFormalAdapter() {
        var formal=order(ConfirmedV1Input.Channel.PDA);
        assertThrows(IllegalArgumentException.class,()->ConfirmedV1Adapter.adapt(input(withReceipt(formal,temporary().successReceipt()))));
    }
}
