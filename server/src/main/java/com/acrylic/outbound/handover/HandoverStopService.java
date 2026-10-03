package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Identity and source locks serialize the first request; historical retries authenticate before returning original bytes. */
@Service
final class HandoverStopService {
    static final String ROUTE = "handover.stop";
    private final HandoverStopAccess access;
    private final HandoverStopStore store;
    private final HandoverStopReceipts receipts;
    private final HandoverStopWriter writer;
    private final IdempotencyRequests requests;
    private final TransactionTemplate read, write;

    HandoverStopService(HandoverStopAccess access, HandoverStopStore store, HandoverStopReceipts receipts,
            HandoverStopWriter writer, IdempotencyRequests requests, PlatformTransactionManager manager) {
        this.access = access; this.store = store; this.receipts = receipts; this.writer = writer; this.requests = requests;
        read = template(manager, true); write = template(manager, false);
    }

    byte[] execute(HandoverStopCommand command, String requestId) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Handover stop must own the outer transaction boundary");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400, "INVALID_REQUEST_ID", "请求追踪标识无效");
        byte[] original = read.execute(s -> replay(command, access.read(command.caseId())));
        if (original != null) return original;
        return Objects.requireNonNull(write.execute(s -> {
            var context = access.lock(command);
            byte[] committed = replay(command, context); if (committed != null) return committed;
            access.requireCurrent(command, context);
            return writer.record(command, context, requestId);
        }));
    }

    private byte[] replay(HandoverStopCommand command, HandoverStopAccess.Context context) {
        var row = store.original(command.caseId()); var pack = store.recovery(command.caseId());
        var resource = requests.find(key(command, context), command.digest());
        if (row == null && pack == null && resource == null) return null;
        var receipt = receipts.verify(row, pack, context);
        if (!receipt.path("originalCommand").equals(command.bound())) throw HandoverCommand.conflict();
        HandoverCommand.corruptUnless(resource != null && resource.toString().equals(receipt.path("receiptId").asText()));
        return (byte[]) row.get("receipt_bytes");
    }

    static IdempotencyRequests.Key key(HandoverStopCommand command, HandoverStopAccess.Context context) {
        return new IdempotencyRequests.Key(context.actor().userId(), ROUTE, command.key().toString());
    }

    private TransactionTemplate template(PlatformTransactionManager manager, boolean readonly) {
        var result = new TransactionTemplate(manager); result.setReadOnly(readonly);
        result.setIsolationLevel(readonly ? TransactionDefinition.ISOLATION_REPEATABLE_READ : TransactionDefinition.ISOLATION_READ_COMMITTED);
        return result;
    }
}
