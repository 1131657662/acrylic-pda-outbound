package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverCaseCursorTest {
    private final UUID warehouse = UUID.randomUUID(), order = UUID.randomUUID();
    @Test void timestampPrecisionAndTieBreakerSurviveContinuation() {
        var cursor = new HandoverCaseCursor(warehouse, order, LocalDateTime.parse("2026-10-01T01:02:03.123456"), UUID.randomUUID());
        assertThat(HandoverCaseCursor.parse(cursor.encode(), warehouse, order)).isEqualTo(cursor);
    }
    @Test void absentCursorStartsAtTheFirstPage() {
        assertThat(HandoverCaseCursor.parse(null, warehouse, order)).isNull();
    }
    @Test void anotherWarehouseCannotReusePosition() {
        var cursor = new HandoverCaseCursor(warehouse, order, LocalDateTime.now(), UUID.randomUUID());
        assertThatThrownBy(() -> HandoverCaseCursor.parse(cursor.encode(), UUID.randomUUID(), order)).isInstanceOf(BusinessException.class);
    }
    @Test void anotherOrderCannotReusePosition() {
        var cursor = new HandoverCaseCursor(warehouse, order, LocalDateTime.now(), UUID.randomUUID());
        assertThatThrownBy(() -> HandoverCaseCursor.parse(cursor.encode(), warehouse, UUID.randomUUID())).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"", "invalid!", "eHx5fHp8dA", "eA"})
    void malformedPositionFailsClosed(String position) {
        assertThatThrownBy(() -> HandoverCaseCursor.parse(position, warehouse, order)).isInstanceOf(BusinessException.class);
    }
    @Test void oversizedPositionFailsClosed() {
        assertThatThrownBy(() -> HandoverCaseCursor.parse("A".repeat(301), warehouse, order)).isInstanceOf(BusinessException.class);
    }
}
