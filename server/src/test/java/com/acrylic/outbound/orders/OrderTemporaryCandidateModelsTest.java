package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.orders.OrderTemporaryCandidateModels.*;
import static org.assertj.core.api.Assertions.*;

class OrderTemporaryCandidateModelsTest {
    @Test void defaultWindowUsesShanghaiOrderCreationDay() {
        var range=Range.resolve(new Filter(null,null,1),"2026-09-24T16:00:00Z");
        assertThat(range.fromDate()).isEqualTo(LocalDate.parse("2026-09-18"));
        assertThat(range.throughDate()).isEqualTo(LocalDate.parse("2026-10-02"));
        assertThat(range.start().toString()).isEqualTo("2026-09-17T16:00:00Z");
        assertThat(range.end().toString()).isEqualTo("2026-10-02T16:00:00Z");
    }
    @Test void invalidRangesCannotMasqueradeAsEmptyCandidates() {
        var day=LocalDate.parse("2026-09-25");
        for(var filter:java.util.List.of(new Filter(day,null,1),new Filter(null,day,1),
            new Filter(day,day.minusDays(1),1),new Filter(day,day.plusDays(366),1)))
            assertThatThrownBy(()->Range.resolve(filter,"2026-09-25T00:00:00Z")).isInstanceOf(BusinessException.class);
        assertThat(Range.resolve(new Filter(day,day.plusDays(365),1),"2026-09-25T00:00:00Z").fromDate()).isEqualTo(day);
    }
    @Test void receiptHintIsStableAndContainsNoChangingCandidateFacts() throws Exception {
        var receipt=new OrderViews.Receipt("receipt","order",2,"3","READY");
        var json=new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(receipt));
        assertThat(json.at("/temporaryCandidateCheck/path").asText()).isEqualTo("/orders/order/temporary-candidates");
        assertThat(json.at("/temporaryCandidateCheck/advisoryOnly").asBoolean()).isTrue();
        assertThat(json.has("candidates")).isFalse();
    }
}
