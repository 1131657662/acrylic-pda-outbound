package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryReleaseNumberSnapshotTest {
    @Test void preservesCounterBeyondHighestExistingOrderNumber() {
        var table=counter(50); var values=RecoveryReleaseNumberSnapshot.counters(table);
        RecoveryReleaseNumberSnapshot.orders(orders("FH202610100002"),values);
        assertThat(values).containsExactlyEntriesOf(Map.of(LocalDate.of(2026,10,10),50));
    }

    @Test void rejectsMissingCounterForPreviouslyIssuedNumber() {
        assertThatThrownBy(()->RecoveryReleaseNumberSnapshot.orders(orders("FH202610100002"),new TreeMap<>()))
            .hasMessage("RECOVERY_NUMBER_ORDER_UNCOVERED");
    }

    @Test void rejectsReusedNumberAcrossOriginalOrders() {
        var snapshot=orders("FH202610100002");
        ((com.fasterxml.jackson.databind.node.ArrayNode)snapshot.path("tables").path("outbound_order").path("rows"))
            .addArray().add(b64("other-id")).add(b64("FH202610100002"));
        assertThatThrownBy(()->RecoveryReleaseNumberSnapshot.orders(snapshot,RecoveryReleaseNumberSnapshot.counters(counter(50))))
            .hasMessage("RECOVERY_NUMBER_DUPLICATE");
    }

    @Test void rejectsDuplicateCounterDate() {
        var table=counter(50); table.withArray("rows").add(table.path("rows").get(0).deepCopy());
        assertThatThrownBy(()->RecoveryReleaseNumberSnapshot.counters(table)).hasMessage("RECOVERY_NUMBER_SNAPSHOT_SEQUENCE");
    }

    private static ObjectNode counter(int value) {
        var table=SubmissionJson.object(); table.putArray("columns").add("business_date").add("last_sequence");
        table.putArray("rows").addArray().add(b64("2026-10-10")).add(b64(Integer.toString(value))); return table;
    }
    private static ObjectNode orders(String number) {
        var result=SubmissionJson.object(); var table=result.putObject("tables").putObject("outbound_order");
        table.putArray("columns").add("id").add("order_no"); table.putArray("rows").addArray().add(b64("order-id")).add(b64(number)); return result;
    }
    private static String b64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
