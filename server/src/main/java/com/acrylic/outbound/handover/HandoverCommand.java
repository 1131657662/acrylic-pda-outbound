package com.acrylic.outbound.handover;

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

/** R07 handover command kernel types. See docs/parallel/R07/INTERFACE_PROPOSAL.md and HANDOVER_DESIGN.md. */
final class HandoverCommand {
    private HandoverCommand() { }

    static final String ROUTE_SCOPE = "r07.handover.v1";
    /** Kept out of the TEMP permission model: handover has its own confirmation gate. */
    static final List<String> KINDS = List.of("REQUEST", "APPROVE", "PREPARE", "ACTIVATE");
    static final Set<String> KINDS_SET = Set.copyOf(KINDS);
    static final String APPROVE_PERMISSION = "order:handover";
    static final String RESUME_PERMISSION = "order:resume";

    static BusinessException stale() { return new BusinessException(409, "HANDOVER_CONTEXT_CHANGED", "交接上下文已变化，请重新核对"); }
    static BusinessException missing() { return new BusinessException(404, "HANDOVER_CASE_NOT_FOUND", "交接案件不存在或无权访问"); }
    static BusinessException unsupported() { return new BusinessException(409, "HANDOVER_COMMAND_NOT_OPEN", "此交接命令尚未开放"); }
    static BusinessException denied() { return new BusinessException(403, "PERMISSION_DENIED", "没有执行此交接命令的权限"); }
    static BusinessException conflict() { return new BusinessException(409, "IDEMPOTENCY_CONTENT_MISMATCH", "同一原键对应不同命令，请保留原意图"); }
    static BusinessException corrupt() { return new BusinessException(409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE", "原命令证据不可核验，请保留资料"); }
    /** One integrity assertion: a stored receipt that cannot be re-proved is a refusal, never a silent retry. */
    static void corruptUnless(boolean valid) { if (!valid) throw corrupt(); }

    /** Parsed command body: the original key is the client-minted commandId, as in the R06 command chain. */
    static final class Input {
        private final byte[] bytes;private final JsonNode body;private final String digest;
        Input(byte[] bytes) {
            this.bytes=SubmissionJson.canonical(SubmissionJson.parse(bytes)).getBytes(StandardCharsets.UTF_8);
            this.body=SubmissionJson.parse(this.bytes);
            this.digest=sha(this.bytes);
        }
        static Input parse(byte[] raw) {
            if (raw == null || raw.length == 0 || raw.length > 262_144) throw new BusinessException(400, "INVALID_HANDOVER_COMMAND", "命令格式或资源大小无效");
            var parsed = new Input(raw);
            if (parsed.body.path("commandVersion").asInt(0) != 1 || !KINDS_SET.contains(parsed.kind()))
                throw new BusinessException(400, "INVALID_HANDOVER_COMMAND", "命令格式或资源大小无效");
            if (!parsed.expected().isObject()) throw new BusinessException(400, "INVALID_HANDOVER_COMMAND", "命令格式或资源大小无效");
            return parsed;
        }
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
            if (!permissions.contains(APPROVE_PERMISSION) && !permissions.contains(RESUME_PERMISSION)) throw denied();
            return new Actor(user.id(),warehouse,Long.toString(user.authVersion()),principal,List.copyOf(permissions),warehouses);
        }
    }

    record Actor(UUID id,UUID warehouse,String authVersion,PdaSessions.Principal pda,List<String> permissions,List<String> warehouses) {
        boolean mayApprove() { return permissions.contains(APPROVE_PERMISSION) || permissions.contains(RESUME_PERMISSION); }
    }

    /** Serialisation points: the source scope row and the case row, both taken FOR UPDATE in the write path. */
    @Component
    static final class Locks {
        private final JdbcTemplate jdbc;
        Locks(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        long scope(UUID scope) {
            var rows=jdbc.queryForList("SELECT id FROM submission_scope WHERE id=? FOR UPDATE",Ids.bytes(scope));
            if (rows.size()!=1) throw missing();return 1;
        }
        long kase(UUID id) {
            var rows=jdbc.queryForList("SELECT row_version FROM handover_case WHERE id=? FOR UPDATE",Ids.bytes(id));
            if (rows.size()!=1) throw missing();return 1;
        }
    }

    /**
     * Expected context. REQUEST is anchored on the source scope (no case exists yet); the other three are
     * anchored on the case id plus its optimistic version, so a stale page can never approve or activate twice.
     */
    static final class Expected {
        private Expected() { }
        static ObjectNode forRequest(JsonNode scopeFacts,long userAuthVersion) {
            var expected=SubmissionJson.object();
            for (String key : List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound"))
                expected.set(key,scopeFacts.get(key));
            expected.put("userAuthVersion",Long.toString(userAuthVersion));
            return expected;
        }
        static ObjectNode forCase(JsonNode caseFacts,long userAuthVersion) {
            var expected=SubmissionJson.object();
            for (String key : List.of("caseId","caseVersion","status")) expected.set(key,caseFacts.get(key));
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
        default boolean requiresApproval() { return commandType().equals("APPROVE") || commandType().equals("PREPARE") || commandType().equals("ACTIVATE"); }
    }

    record Write(UUID requestId,UUID receiptId,UUID eventId,UUID caseId,Instant recordedAt,Input command,JsonNode caseFacts,JsonNode actor) {
        Write { caseFacts=caseFacts.deepCopy();actor=actor.deepCopy(); }
        @Override public JsonNode caseFacts() { return caseFacts.deepCopy(); }
        @Override public JsonNode actor() { return actor.deepCopy(); }
        long caseVersion() { return caseFacts.path("caseVersion").asLong(0); }
    }

    static String sha(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
