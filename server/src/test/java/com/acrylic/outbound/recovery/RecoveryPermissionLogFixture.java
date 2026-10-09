package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.CRC32;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Minimal framed MySQL ROW transaction; SQL and authority state are verified separately on real MySQL. */
final class RecoveryPermissionLogFixture {
    final String schema = "original_test";
    final UUID warehouse = UUID.randomUUID();
    final RecoveryPermissionBoundary.Request expected = new RecoveryPermissionBoundary.Request(
        new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), schema, "1"), UUID.randomUUID(), UUID.randomUUID());
    final ObjectNode begin = boundary(), end = boundary();
    final ByteArrayOutputStream log = new ByteArrayOutputStream();
    long micros = Instant.parse("2026-10-10T01:00:00.500Z").getEpochSecond() * 1000000 + 500000;
    int start, finish;

    RecoveryPermissionLogFixture() {
        log.writeBytes(new byte[]{(byte)0xfe, 'b', 'i', 'n'}); format(); event(35, new byte[8]); start = log.size();
        transaction("submission"); finish = log.size();
        begin.put("observedAt", "2026-10-10T01:00:00Z").put("binlogPosition", Integer.toString(start));
        end.put("observedAt", "2026-10-10T01:00:01Z").put("binlogPosition", Integer.toString(finish));
    }

    private ObjectNode boundary() {
        var value = SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_PERMISSION_BOUNDARY")
            .put("mysqlServerUuid", expected.database().mysqlServerUuid().toString()).put("databaseSchema", schema)
            .put("sourceRecoveryEpoch", "1").put("userId", expected.operatorId().toString()).put("deviceId", expected.deviceId().toString())
            .put("binlogFile", "binlog.000001");
        var effective = value.putObject("effective").put("userActive", true).put("userAuthVersion", "1")
            .put("warehouseId", warehouse.toString()).put("deviceStatus", "ACTIVE").put("deviceVersion", "1").put("deviceTemporaryOrderEnabled", true);
        effective.putArray("permissions").add("pda:login").add("pda:scan"); effective.putArray("warehouseIds").add(warehouse.toString()); return value;
    }

    void transaction(String table) {
        byte[] gtid = new byte[56]; gtid[0] = 1; gtid[25] = 2; put(gtid, 42, micros, 7); event(34, gtid);
        query("BEGIN"); var map = new ByteArrayOutputStream(); map.writeBytes(new byte[]{1,0,0,0,0,0,0,0});
        map.write(schema.length()); map.writeBytes(schema.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); map.write(0);
        map.write(table.length()); map.writeBytes(table.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); map.write(0);
        map.writeBytes(new byte[]{1,3,0,0}); event(19, map.toByteArray());
        event(31, new byte[]{1,0,0,0,0,0,1,0,2,0,1,1,1,0,1,0,0,0,0,2,0,0,0});
        byte[] xid = new byte[8]; xid[0] = 1; event(16, xid);
    }

    void query(String sql) {
        byte[] text = sql.getBytes(java.nio.charset.StandardCharsets.US_ASCII), body = new byte[14 + text.length];
        System.arraycopy(text, 0, body, 14, text.length); event(2, body);
    }

    void event(int type, byte[] body) {
        byte[] value = new byte[23 + body.length]; value[4] = (byte)type;
        put(value, 5, 1, 4); put(value, 9, value.length, 4); put(value, 13, log.size() + value.length, 4);
        System.arraycopy(body, 0, value, 19, body.length); crc(value); log.writeBytes(value);
    }

    private void format() {
        byte[] body = new byte[99]; body[0] = 4;
        byte[] version = "8.0.46".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(version, 0, body, 2, version.length); body[56] = 19;
        body[57 + 18] = 8; body[57 + 29] = 10; body[57 + 30] = 10; body[57 + 31] = 10; body[98] = 1; event(15, body);
    }

    RecoveryPermissionContinuity.Request request() { return request(log.toByteArray()); }
    RecoveryPermissionContinuity.Request request(byte[] bytes) {
        begin.put("binlogPrefixSha256", sha256(Arrays.copyOf(bytes, begin.path("binlogPosition").asInt())));
        end.put("binlogPrefixSha256", sha256(bytes));
        byte[] first = RecoveryControlJson.encode(begin), last = RecoveryControlJson.encode(end);
        return new RecoveryPermissionContinuity.Request(expected, first, sha256(first), last, sha256(last), bytes, sha256(bytes));
    }

    void replaceTransaction(String table) {
        byte[] prefix = Arrays.copyOf(log.toByteArray(), start); log.reset(); log.writeBytes(prefix); transaction(table);
        finish = log.size(); end.put("binlogPosition", Integer.toString(finish));
    }

    static void crc(byte[] event) {
        var crc = new CRC32(); crc.update(event, 0, event.length - 4); put(event, event.length - 4, crc.getValue(), 4);
    }
    static void put(byte[] bytes, int offset, long value, int length) {
        for (int index = 0; index < length; index++) bytes[offset + index] = (byte)(value >>> (index * 8));
    }
}
