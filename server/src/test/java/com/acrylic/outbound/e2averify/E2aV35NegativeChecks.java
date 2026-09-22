package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** V35 structural controls and negative cases. Synthetic rows are database-only test facts. */
public final class E2aV35NegativeChecks {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TABLE = "source_evidence_intake_receipt";
    private static final String INSERT = "INSERT INTO " + TABLE + " (id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,scope_id,submission_id,work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,signature_key_id,signed_receipt,object_id,evidence_set_id,evidence_verification_id,recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Seed seed;

    public E2aV35NegativeChecks(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.seed = Seed.load(jdbc);
    }

    public Result verify(Path evidence) throws Exception {
        Objects.requireNonNull(evidence);
        Files.createDirectories(evidence.toAbsolutePath().normalize());
        int beforeCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        Seed prepared = prepareSeed(seed);
        this.seedRef = prepared;
        ArrayNode cases = JSON.createArrayNode();
        control(cases, "OPEN", null, null, null);
        control(cases, "PAGE", seed.objectId(), null, null);
        control(cases, "STOP", seed.objectId(), null, null);
        control(cases, "VERIFY", null, seed.setId(), seed.verificationId());
        negative(cases, "status-check", b -> b.status = "BOGUS");
        negative(cases, "version-check", b -> b.version = 2);
        negative(cases, "request-digest-check", b -> b.requestSha = "0");
        negative(cases, "response-size-check", b -> b.response = new byte[0]);
        negative(cases, "open-reference-shape", b -> b.object = seed.objectId());
        negative(cases, "page-reference-shape", b -> { b.operation = "PAGE"; b.object = null; });
        negative(cases, "verify-reference-shape", b -> { b.operation = "VERIFY"; b.set = null; b.verification = null; });
        negative(cases, "scope-composite-fk", b -> b.scope = uuid());
        negative(cases, "submission-round-fk", b -> b.round++);
        negative(cases, "natural-key", this::duplicateNaturalKey);
        negative(cases, "immutable-update", this::immutableUpdate);
        negative(cases, "immutable-delete", this::immutableDelete);
        ObjectNode report = JSON.createObjectNode().put("generatedAt", Instant.now().toString());
        report.set("cases", cases);
        int afterCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        if (beforeCount != afterCount) throw new AssertionError("V35 negative matrix changed intake row count");
        report.put("beforeCount", beforeCount).put("afterCount", afterCount);
        Path target = evidence.toAbsolutePath().normalize().resolve("v35-negative.json");
        Files.writeString(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardOpenOption.CREATE_NEW);
        for (var node : cases) {
            String name = node.path("name").asText();
            Files.writeString(evidence.toAbsolutePath().normalize().resolve("v35-negative-" + name + ".json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node), StandardOpenOption.CREATE_NEW);
        }
        return new Result(cases.size(), target);
    }

    private Seed seedRef;
    private void control(ArrayNode cases, String operation, byte[] object, byte[] set, byte[] verification) {
        CaseBuilder b = new CaseBuilder(seedRef, operation, object, set, verification);
        boolean[] inserted = {false};
        transaction.executeWithoutResult(status -> { insert(b); inserted[0] = true; status.setRollbackOnly(); });
        cases.add(JSON.createObjectNode().put("name", "control-" + operation).put("accepted", inserted[0]).put("rolledBack", true));
    }

    private void negative(ArrayNode cases, String name, java.util.function.Consumer<CaseBuilder> change) {
        CaseBuilder b = new CaseBuilder(seedRef, "OPEN", null, null, null);
        Failure failure = new Failure();
        transaction.executeWithoutResult(status -> {
            try { change.accept(b); if (!"natural-key".equals(name) && !name.startsWith("immutable-")) insert(b); failure.unexpected = true; }
            catch (DataAccessException error) { failure.capture(error); }
            status.setRollbackOnly();
        });
        if (failure.unexpected || failure.sqlState == null) throw new AssertionError(name + " unexpectedly succeeded");
        if (!failure.matches(name)) throw new AssertionError(name + " wrong SQL error " + failure.sqlState + "/" + failure.vendorCode);
        ObjectNode row = JSON.createObjectNode().put("name", name).put("sqlState", failure.sqlState).put("vendorCode", failure.vendorCode);
        cases.add(row);
    }

    private void duplicateNaturalKey(CaseBuilder b) {
        insert(b);
        b.id = uuid();
        try { insert(b); throw new AssertionError("duplicate accepted"); }
        catch (DataAccessException expected) { throw expected; }
    }

    private void immutableUpdate(CaseBuilder b) {
        insert(b);
        jdbc.update("UPDATE " + TABLE + " SET receipt_status='PARTIAL' WHERE id=?", b.id);
    }

    private void immutableDelete(CaseBuilder b) {
        insert(b);
        jdbc.update("DELETE FROM " + TABLE + " WHERE id=?", b.id);
    }

    private void insert(CaseBuilder b) {
        if (jdbc.update(INSERT, b.id, b.version, b.principal, b.route, b.originalKey, null, b.warehouse, b.scope, b.submission,
            b.round, b.device, b.order, b.authorization, b.operator, b.epoch, b.operation, b.status, b.request,
            b.requestSha, b.response, b.responseSha, "synthetic-test-key", "synthetic-test-receipt", b.object, b.set,
            b.verification, java.sql.Timestamp.from(Instant.now())) != 1) throw new IllegalStateException("intake insert affected zero/multiple rows");
    }

    private Seed prepareSeed(Seed s) {
        byte[] object = uuid(), set = uuid(), verification = uuid();
        jdbc.update("INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at) VALUES(?,?,?,?,?,'LOCAL_FROZEN_PAYLOAD',?, ?,1,?,?,?,UTC_TIMESTAMP(3))", object,s.warehouse,s.scope,s.submission,s.round,uuid(),sha("object".getBytes()),"synthetic/v35-object",s.device,s.operator);
        jdbc.update("INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at) VALUES(?,?,?,?,?,?,?, ?,1,1,0,UTC_TIMESTAMP(3))", set,s.warehouse,s.scope,s.submission,s.round,object,"set".getBytes(),sha("set".getBytes()));
        jdbc.update("INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,verification_version,algorithm_version,set_sha256,result,known_facts_sha256,findings_bytes,verified_at) VALUES(?,?,?,?,?,1,1,?,'PARTIAL',?,?,UTC_TIMESTAMP(3))", verification,set,s.scope,s.submission,s.round,sha("set".getBytes()),sha("facts".getBytes()),"findings".getBytes());
        return new Seed(s.principal,s.warehouse,s.scope,s.submission,s.device,s.order,s.authorization,s.operator,object,set,verification,s.round,s.epoch);
    }

    private static byte[] uuid() { return bytes(UUID.randomUUID()); }
    private static byte[] bytes(UUID id) { var out = java.nio.ByteBuffer.allocate(16); out.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()); return out.array(); }
    private static String sha(byte[] value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public record Result(int cases, Path evidence) { }
    private static final class Failure { String sqlState; int vendorCode; boolean unexpected; void capture(Throwable error) { SQLException sql = find(error); if (sql != null) { sqlState = sql.getSQLState(); vendorCode = sql.getErrorCode(); } } boolean matches(String name) { if (name.equals("natural-key")) return vendorCode == 1062; if (name.startsWith("immutable-")) return vendorCode == 1644; if (name.contains("composite-fk") || name.contains("round-fk") || name.contains("reference-shape")) return vendorCode == 1452 || vendorCode == 3819; return vendorCode == 3819 || vendorCode == 1452; } }
    private static SQLException find(Throwable error) { for (Throwable t = error; t != null; t = t.getCause()) if (t instanceof SQLException sql) return sql; return null; }
    private static final class CaseBuilder {
        byte[] id=uuid(), principal, originalKey=uuid(), warehouse, scope, submission, device, order, authorization, operator, object, set, verification;
        int version=1; String route="temporary.source", operation, status="RECEIVED", requestSha, responseSha; byte[] request="request".getBytes(), response="response".getBytes(); long round, epoch;
        CaseBuilder(Seed s,String op,byte[] object,byte[] set,byte[] verification){principal=s.principal;warehouse=s.warehouse;scope=s.scope;submission=s.submission;device=s.device;order=s.order;authorization=s.authorization;operator=s.operator;round=s.round;epoch=s.epoch;operation=op;this.object=object;this.set=set;this.verification=verification;requestSha=sha(request);responseSha=sha(response);}
    }
    public record Seed(byte[] principal, byte[] warehouse, byte[] scope, byte[] submission, byte[] device, byte[] order, byte[] authorization, byte[] operator, byte[] objectId, byte[] setId, byte[] verificationId, long round, long epoch) {
        static Seed load(JdbcTemplate j) {
            return j.queryForObject("SELECT s.operator_id,s.warehouse_id,c.id,s.id,c.creator_device_id,c.client_order_id,s.temporary_authorization_id,s.operator_id,s.work_round,s.source_recovery_epoch FROM submission s JOIN submission_scope c ON c.id=s.scope_id WHERE c.creator_device_id IS NOT NULL ORDER BY s.id LIMIT 1", (rs,n) -> new Seed(rs.getBytes(1),rs.getBytes(2),rs.getBytes(3),rs.getBytes(4),rs.getBytes(5),rs.getBytes(6),rs.getBytes(7),rs.getBytes(8),null,null,null,rs.getLong(9),rs.getLong(10)));
        }
    }
}
