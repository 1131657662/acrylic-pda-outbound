package com.acrylic.outbound.correction;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * R07-3 correction command kernel types.
 *
 * <p>Mirrors the R07-1 handover kernel (docs/parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md §6 says to reuse that
 * discipline instead of inventing a third design): the original key is the client-minted commandId, the write path is
 * one transaction, the receipt is immutable and signed, and one audit event closes each receipt. Error codes are the
 * draft set from §5 and stay harmless until the command surface is switched on.
 */
final class CorrectionCommand {
    private CorrectionCommand() { }

    static final String ROUTE_SCOPE = "r07.correction.v1";
    static final List<String> KINDS = List.of("CREATE", "APPROVE", "WITHDRAW");
    static final Set<String> KINDS_SET = Set.copyOf(KINDS);
    /** Both codes are registered by V45 - the R07-1 lesson was an approval gate whose code was never registered. */
    static final String CORRECT_PERMISSION = "outbound:correct";
    static final String APPROVE_PERMISSION = "correction:approve";

    static BusinessException notOpen() { return new BusinessException(409, "CORRECTION_COMMAND_NOT_OPEN", "此更正命令尚未开放"); }
    static BusinessException stale() { return new BusinessException(409, "CORRECTION_CONTEXT_CHANGED", "更正上下文已变化，请重新核对"); }
    static BusinessException missing() { return new BusinessException(404, "CORRECTION_DOCUMENT_NOT_FOUND", "更正单不存在或无权访问"); }
    static BusinessException denied() { return new BusinessException(403, "CORRECTION_PERMISSION_DENIED", "没有执行此更正命令的权限"); }
    static BusinessException conflict() { return new BusinessException(409, "IDEMPOTENCY_CONTENT_MISMATCH", "同一原键对应不同命令，请保留原意图"); }
    static BusinessException corrupt() { return new BusinessException(409, "CORRECTION_RECEIPT_INTEGRITY_FAILURE", "原命令证据不可核验，请保留资料"); }
    // Draft §5 codes, ready for the effect/approval/reconciliation steps. None is reachable while the surface is off.
    static BusinessException alreadyEffective() { return new BusinessException(409, "CORRECTION_ALREADY_EFFECTIVE", "该更正已生效，不能重复生效"); }
    static BusinessException targetUnchanged() { return new BusinessException(409, "CORRECTION_LINE_TARGET_UNCHANGED", "目标值与当前版本一致，无需更正"); }
    static BusinessException evidenceRequired() { return new BusinessException(400, "CORRECTION_EVIDENCE_REQUIRED", "实物更正行必须附核验凭据"); }
    static BusinessException windowExceeded() { return new BusinessException(409, "CORRECTION_WINDOW_EXCEEDED", "超出 3 天补录窗口，请走原单处理"); }
    static BusinessException ledgerConflict() { return new BusinessException(409, "CORRECTION_LEDGER_CONFLICT", "账务版本已变化，请重新核对"); }
    static BusinessException cycleReopeningExists() { return new BusinessException(409, "CYCLE_REOPENING_EXISTS", "原出库批次已重开过一次"); }
    static BusinessException attachmentLimit() { return new BusinessException(400, "ATTACHMENT_LIMIT_EXCEEDED", "附件超出单文件 10MB/单次 50MB/单条 5 个限额"); }

    /** Parsed command body: the original key is the client-minted commandId, as in the R06/R07-1 chains. */
    static final class Input {
        private final byte[] bytes;private final JsonNode body;private final String digest;
        Input(byte[] bytes) {
            this.bytes=SubmissionJson.canonical(SubmissionJson.parse(bytes)).getBytes(StandardCharsets.UTF_8);
            this.body=SubmissionJson.parse(this.bytes);
            this.digest=sha(this.bytes);
        }
        static Input parse(byte[] raw) {
            if (raw==null || raw.length==0 || raw.length>262_144) throw invalid();
            var parsed=new Input(raw);
            if (parsed.body.path("commandVersion").asInt(0)!=1 || !KINDS_SET.contains(parsed.kind())) throw invalid();
            if (!parsed.expected().isObject()) throw invalid();
            return parsed;
        }
        private static BusinessException invalid() { return new BusinessException(400,"INVALID_CORRECTION_COMMAND","命令格式或资源大小无效"); }
        byte[] bytes() { return bytes.clone(); }
        JsonNode body() { return body.deepCopy(); }
        JsonNode expected() { return body.path("expected").deepCopy(); }
        String kind() { return text("commandType"); }
        String key() { return text("commandId"); }
        String digest() { return digest; }
        String text(String field) { var value=body.get(field);return value==null||value.isNull()?null:value.asText(); }
        UUID uuid(String field) { String value=text(field);return value==null?null:UUID.fromString(value); }
        long number(String field) { return Long.parseLong(text(field)); }
    }

    @Component
    static final class Identity {
        private final Access web;private final PdaAccess pda;private final IdentityStore identities;
        Identity(Access web,PdaAccess pda,IdentityStore identities) { this.web=web;this.pda=pda;this.identities=identities; }
        Actor current(UUID warehouse) {
            var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            PdaSessions.Principal principal=authentication!=null && authentication.getPrincipal() instanceof PdaSessions.Principal?pda.current():null;
            SessionUser user=principal==null?web.current():identities.loadUserByUsername(principal.username());
            if (principal!=null && !principal.warehouseId().equals(warehouse)) throw missing();
            var warehouses=identities.warehouses(user.id()).stream().map(w->w.id().toString()).sorted().toList();
            if (!warehouses.contains(warehouse.toString())) throw missing();
            var permissions=identities.permissions(user.id());
            if (!permissions.contains(CORRECT_PERMISSION)) throw denied();
            return new Actor(user.id(),warehouse,Long.toString(user.authVersion()),principal,List.copyOf(permissions),warehouses);
        }
    }

    /** {@code mayApprove} is the supervisor gate: without the registered approval code, approving is impossible. */
    record Actor(UUID id,UUID warehouse,String authVersion,PdaSessions.Principal pda,List<String> permissions,List<String> warehouses) {
        boolean mayApprove() { return permissions.contains(APPROVE_PERMISSION); }
    }

    /** Serialisation points: the order row and the document row, both taken FOR UPDATE in the write path. */
    @Component
    static final class Locks {
        private final JdbcTemplate jdbc;
        Locks(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        long order(UUID order) {
            var rows=jdbc.queryForList("SELECT id FROM outbound_order WHERE id=? FOR UPDATE",Ids.bytes(order));
            if (rows.size()!=1) throw missing();return 1;
        }
        long document(UUID document) {
            var rows=jdbc.queryForList("SELECT row_version FROM correction_document WHERE id=? FOR UPDATE",Ids.bytes(document));
            if (rows.size()!=1) throw missing();return 1;
        }
    }

    /**
     * Expected context (§3): CREATE anchors on the order's current result version plus document digest; APPROVE and
     * WITHDRAW anchor on the document id plus its digest, so a stale page can never approve an edited document.
     */
    static final class Expected {
        private Expected() { }
        static ObjectNode forCreate(JsonNode orderFacts,long userAuthVersion) {
            var expected=SubmissionJson.object();
            for (String key : List.of("orderId","currentResultVersion","currentResultEffectiveCount","currentResultEffectiveWeightG"))
                expected.set(key,orderFacts.get(key));
            expected.put("userAuthVersion",Long.toString(userAuthVersion));
            return expected;
        }
        static ObjectNode forDocument(JsonNode documentFacts,long userAuthVersion) {
            var expected=SubmissionJson.object();
            for (String key : List.of("documentId","documentSha256","status","expectedResultVersion"))
                expected.set(key,documentFacts.get(key));
            expected.put("userAuthVersion",Long.toString(userAuthVersion));
            return expected;
        }
        static void validate(JsonNode expected,ObjectNode current) {
            if (!current.equals(expected)) throw stale();
        }
    }

    /** What an operation returns: a business decision object, or null for a read-only decision. */
    interface Operation {
        String commandType();
        JsonNode execute(Write write);
        default boolean requiresApproval() { return !commandType().equals("CREATE"); }
    }

    record Write(UUID requestId,UUID receiptId,UUID eventId,UUID documentId,UUID orderId,Instant recordedAt,Input command,
        JsonNode anchor,JsonNode actor) {
        Write { anchor=anchor.deepCopy();actor=actor.deepCopy(); }
        @Override public JsonNode anchor() { return anchor.deepCopy(); }
        @Override public JsonNode actor() { return actor.deepCopy(); }
    }

    static String sha(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
