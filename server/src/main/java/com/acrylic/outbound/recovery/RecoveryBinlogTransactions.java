package com.acrylic.outbound.recovery;

import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;
import static com.acrylic.outbound.recovery.RecoveryBinlogFile.*;

/** Conservative ROW-only logged interval. Any authority-table write or unsupported event refuses the whole window. */
final class RecoveryBinlogTransactions {
    private static final Set<String> BUSINESS = Set.of("temporary_preparation", "temporary_authorization", "temporary_capture",
        "temporary_scan", "temporary_submission", "temporary_order", "submission", "submission_scope", "submission_payload",
        "source_evidence_object", "source_evidence_set", "source_evidence_item", "bale_commitment", "operation_event",
        "device_challenge", "device_scan_event", "device_audit_event");
    private final String schema;
    private final Instant from, through;
    private final Map<Long,String> tables = new HashMap<>();
    private boolean transaction, begun, rows;
    private Instant previous;
    private long commits, rowEvents;

    private RecoveryBinlogTransactions(String schema, Instant from, Instant through) {
        this.schema = schema; this.from = from; this.through = through;
    }

    static Summary verify(List<Event> events, Interval interval) {
        var reader = new RecoveryBinlogTransactions(interval.schema(), interval.from(), interval.through());
        boolean atStart = false, atEnd = false; boolean prefixOpen = false;
        for (var event : events) {
            if (event.end() <= interval.start()) {
                prefixOpen = prefix(event, prefixOpen);
                if (event.end() == interval.start()) {
                    require(!prefixOpen, "RECOVERY_BINLOG_START_TRANSACTION"); atStart = true;
                }
            } else if (atStart && event.end() <= interval.end()) reader.consume(event);
            if (event.end() == interval.end()) { atEnd = true; break; }
        }
        require(atStart && atEnd && !reader.transaction, "RECOVERY_BINLOG_BOUNDARY");
        return new Summary(reader.commits, reader.rowEvents);
    }

    private static boolean prefix(Event event, boolean open) {
        if (event.type() == 33 || event.type() == 34) return true;
        if (event.type() == 16) return false;
        if (event.type() == 2) return query(event).equals("BEGIN");
        require(Set.of(15, 35, 19, 30, 31, 32).contains(event.type()), "RECOVERY_BINLOG_PREFIX_UNSUPPORTED");
        return open;
    }

    private void consume(Event event) {
        switch (event.type()) {
            case 33, 34 -> begin(event);
            case 2 -> {
                require(transaction && !begun && query(event).equals("BEGIN"), "RECOVERY_BINLOG_STATEMENT"); begun = true;
            }
            case 19 -> table(event);
            case 30, 31, 32 -> row(event);
            case 16 -> commit(event);
            default -> throw new RecoveryControlException("RECOVERY_BINLOG_EVENT_UNSUPPORTED");
        }
    }

    private void begin(Event event) {
        require(!transaction && event.body().length >= 49, "RECOVERY_BINLOG_TRANSACTION");
        require(number(event.body(), 25, 1) == 2, "RECOVERY_BINLOG_COMMIT_TIME");
        long immediate = number(event.body(), 42, 7), original = immediate & ~(1L << 55);
        if ((immediate & (1L << 55)) != 0) original = number(event.body(), 49, 7);
        require(original == (immediate & ~(1L << 55)), "RECOVERY_BINLOG_REPLICATED");
        Instant time = Instant.ofEpochSecond(original / 1000000, original % 1000000 * 1000);
        require(!time.isBefore(from) && time.isBefore(through) && (previous == null || !time.isBefore(previous)), "RECOVERY_BINLOG_COMMIT_TIME");
        previous = time; transaction = true; begun = false; rows = false; tables.clear();
    }

    private void table(Event event) {
        require(transaction && begun, "RECOVERY_BINLOG_TRANSACTION"); byte[] body = event.body();
        int dbLength = Math.toIntExact(number(body, 8, 1)), tableOffset = 10 + dbLength;
        require(number(body, 9 + dbLength, 1) == 0, "RECOVERY_BINLOG_TABLE");
        String database = ascii(body, 9, dbLength); int length = Math.toIntExact(number(body, tableOffset, 1));
        String name = ascii(body, tableOffset + 1, length);
        require(number(body, tableOffset + 1 + length, 1) == 0 && body.length > tableOffset + 2 + length, "RECOVERY_BINLOG_TABLE");
        require(schema.equals(database) && BUSINESS.contains(name), "RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
        tables.put(number(body, 0, 6), name);
    }

    private void row(Event event) {
        require(transaction && begun && tables.containsKey(number(event.body(), 0, 6)), "RECOVERY_BINLOG_ROW_TABLE");
        require(number(event.body(), 8, 2) >= 2 && event.body().length > 10, "RECOVERY_BINLOG_ROW");
        rows = true; rowEvents++;
        if ((number(event.body(), 6, 2) & 1) != 0) tables.clear();
    }

    private void commit(Event event) {
        require(transaction && begun && rows && event.body().length == 8 && number(event.body(), 0, 8) > 0,
            "RECOVERY_BINLOG_TRANSACTION"); transaction = false; commits++; tables.clear();
    }

    private static String query(Event event) {
        byte[] body = event.body(); int schemaLength = Math.toIntExact(number(body, 8, 1));
        int statusLength = Math.toIntExact(number(body, 11, 2)), offset = 13 + statusLength + schemaLength;
        require(number(body, offset, 1) == 0 && number(body, 9, 2) == 0, "RECOVERY_BINLOG_QUERY");
        byte[] sql = Arrays.copyOfRange(body, offset + 1, body.length);
        String text = new String(sql, java.nio.charset.StandardCharsets.UTF_8);
        require(Arrays.equals(sql, text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RECOVERY_BINLOG_TEXT"); return text;
    }

    record Interval(String schema, int start, int end, Instant from, Instant through) { }
    record Summary(long commits, long rowEvents) { }
}
