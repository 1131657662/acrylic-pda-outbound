package com.acrylic.outbound.reports;

import java.util.*;
import java.time.Instant;
import static com.acrylic.outbound.reports.Values.*;

/** Reconciles original confirmations only; never infers a current result or a correction. */
public final class ConfirmedV1Adapter {
    public static ReportSnapshot adapt(ConfirmedV1Input input) {
        Objects.requireNonNull(input, "input");
        require(input.metadata().basis() == ReportSnapshot.Basis.ORIGINAL_CONFIRMED,
                "confirmed-v1 only accepts ORIGINAL_CONFIRMED; current results require a separate snapshot");
        require(input.orders().size() <= 10000, "confirmed-v1 exceeds 10000 orders");
        require(input.orders().stream().mapToLong(o -> o.dispatches().size()).sum() <= 2_000_000,
                "confirmed-v1 exceeds 2000000 dispatches");
        var snapshot = new ReportSnapshot(input.metadata(), input.orders().stream().map(o -> order(o, input.metadata().cutoff())).toList());
        SnapshotValidator.validate(snapshot);
        return snapshot;
    }

    private static OrderSnapshot order(ConfirmedV1Input.Order input, Instant cutoff) {
        var result = input.result();
        require(required(input.id()).equals(result.orderId()), "result belongs to another order");
        required(result.submissionId());
        require(result.version() == 1, "confirmed-v1 requires persisted result version 1");
        for (var dispatch : input.dispatches()) {
            require(input.id().equals(dispatch.orderId()), "dispatch belongs to another order");
            require(result.submissionId().equals(dispatch.submissionId()), "dispatch belongs to another submission");
        }
        var lines = input.dispatches().stream().map(ConfirmedV1Input.Dispatch::line).toList();
        var totals = ConfirmedV1Totals.reconcile(input);
        ConfirmedV1Receipt.validate(input, totals, cutoff);
        var proof = new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.PERSISTED_ORDER_SEQUENCE,
                required(input.sequenceReference()) + " / submission:" + result.submissionId());
        return new OrderSnapshot(input.id(), input.number(), input.revision(), 1, "FORMAL_" + input.channel(),
                input.header(), input.header(), input.confirmedAt(), proof, input.products(), lines,
                lines.stream().map(r -> new OrderSnapshot.EffectiveLine(r.dispatchId(), 1, r.weightGrams())).toList(),
                List.of(), input.exceptions());
    }
    private ConfirmedV1Adapter() { }
}
