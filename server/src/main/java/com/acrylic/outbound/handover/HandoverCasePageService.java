package com.acrylic.outbound.handover;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Discovers cases using the same live identity gate as existing context reads. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class HandoverCasePageService {
    private final HandoverCommand.Identity identity;
    private final HandoverCasePageStore store;
    HandoverCasePageService(HandoverCommand.Identity identity, HandoverCasePageStore store) {
        this.identity = identity; this.store = store;
    }

    public Page read(UUID warehouse, UUID order, int size, String position) {
        identity.current(warehouse);
        if (size < 1 || size > 100) throw HandoverCaseCursor.invalid();
        var cursor = HandoverCaseCursor.parse(position, warehouse, order);
        String orderNo = store.orderNo(warehouse, order);
        var rows = store.rows(warehouse, order, cursor, size + 1);
        boolean more = rows.size() > size;
        var visible = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
        String next = null;
        if (more) {
            var last = visible.getLast();
            next = new HandoverCaseCursor(warehouse, order, last.requestedAt(), last.caseId()).encode();
        }
        var items = visible.stream().map(row -> new Item(row.caseId(), row.status(), row.fromWorkRound(), row.toWorkRound(),
            row.requestedAt().toString(), row.caseVersion())).toList();
        return new Page(warehouse, order, orderNo, items, more, next);
    }

    public record Item(UUID caseId, String status, int fromWorkRound, int toWorkRound, String requestedAt,
        String caseVersion) { }
    public record Page(UUID warehouseId, UUID orderId, String orderNo,
        List<Item> items, boolean hasMore, String nextCursor) { }
}
