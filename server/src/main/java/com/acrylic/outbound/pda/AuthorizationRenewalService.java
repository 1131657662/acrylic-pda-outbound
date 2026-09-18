package com.acrylic.outbound.pda;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Issues a new fixed 48-hour credential while preserving every prior credential row. */
@Service
public class AuthorizationRenewalService {
    private static final long OFFLINE_SECONDS = 48L * 60 * 60;
    private final PdaAccess access;
    private final AssignmentStore assignments;
    private final IdempotencyRequests requests;
    private final JsonText json;
    private final OfflineSigner signer;
    private final AuditWriter audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuthorizationRenewalService(PdaAccess access, AssignmentStore assignments, IdempotencyRequests requests,
        JsonText json, OfflineSigner signer, AuditWriter audit, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.access = access; this.assignments = assignments; this.requests = requests; this.json = json;
        this.signer = signer; this.audit = audit; this.jdbc = jdbc; this.mapper = mapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentStore.View renew(Context context, UUID assignmentId, Command command) {
        var principal = access.lock(context.recoveryEpoch());
        var key = new IdempotencyRequests.Key(principal.userId(), "pda.assignment.renew", context.key());
        var digest = json.digest(new RequestBody(assignmentId, principal.deviceId(), principal.warehouseId(), command));
        var replay = requests.find(key, digest);
        if (replay != null) return assignments.getByAuthorization(replay);

        AssignmentStore.View current;
        try { current = assignments.get(assignmentId); }
        catch (org.springframework.dao.EmptyResultDataAccessException error) {
            throw new BusinessException(404, "ASSIGNMENT_NOT_FOUND", "授权不存在或不属于当前人员及设备");
        }
        checkBinding(current, principal, assignmentId, command);
        lockRenewableOrder(current, command);
        var previous = assignments.lockCurrentAuthorization(assignmentId);
        if (!previous.id().toString().equals(command.authorizationId()))
            throw new BusinessException(409, "OFFLINE_AUTH_REAUTHORIZATION_REQUIRED", "授权已发生变化，请刷新后重试");
        checkTokenVersions(previous.token(), principal);

        var issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expires = issued.plusSeconds(OFFLINE_SECONDS);
        var nextSequence = Math.addExact(previous.sequence(), 1);
        var authorizationId = Ids.next();
        var work = new AssignmentStore.Work(assignmentId, authorizationId, UUID.fromString(current.orderId()), current.revision(),
            Long.parseLong(current.epoch()), principal, current.planSha256(), current.areaNote(), issued, expires);
        var publication = assignments.publication(UUID.fromString(current.orderId()), current.revision());
        var claims = claims(current, principal, authorizationId, previous.id(), nextSequence, publication, issued, expires);
        assignments.insertRenewal(assignmentId, previous.id(), work, signer.sign(claims), nextSequence);
        requests.record(key, digest, authorizationId);
        audit.append(new AuditWriter.Event(principal.userId(), "PDA_AUTHORIZATION_RENEWED", "ORDER_ASSIGNMENT", assignmentId,
            json.encode(new RenewalEvent(assignmentId, current.orderId(), previous.id(), authorizationId, nextSequence))), context.requestId());
        return assignments.get(assignmentId);
    }

    private void checkBinding(AssignmentStore.View current, PdaSessions.Principal principal, UUID assignmentId, Command command) {
        if (!current.deviceId().equals(principal.deviceId().toString()) || !current.operatorId().equals(principal.userId().toString()))
            throw new BusinessException(404, "ASSIGNMENT_NOT_FOUND", "授权不存在或不属于当前人员及设备");
        if (!current.id().equals(assignmentId.toString()) || !current.orderId().equals(command.orderId()) || current.revision() != command.revision()
            || !current.epoch().equals(command.assignmentEpoch()))
            throw new BusinessException(409, "OFFLINE_AUTH_REAUTHORIZATION_REQUIRED", "续期请求未绑定当前订单授权");
        if (command.pendingInputCount() != 0)
            throw new BusinessException(409, "OFFLINE_AUTH_PENDING_INPUT", "仍有未处理输入，先完成或明确拒绝后再续期");
    }

    private void lockRenewableOrder(AssignmentStore.View current, Command command) {
        var order = jdbc.queryForObject("""
            SELECT status,current_revision FROM outbound_order WHERE id=? FOR UPDATE
            """, (rs, n) -> new OrderState(rs.getString(1), rs.getInt(2)), Ids.bytes(UUID.fromString(current.orderId())));
        if (!"ACTIVE".equals(current.status()) || !"ASSIGNED".equals(order.status()) || order.revision() != command.revision())
            throw new BusinessException(409, "ASSIGNMENT_NOT_RENEWABLE", "当前订单已停止、召回或进入提交处理，不能直接续期");
        var submissions = jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE assignment_id=?", Integer.class,
            Ids.bytes(UUID.fromString(current.id())));
        if (submissions != null && submissions > 0)
            throw new BusinessException(409, "ASSIGNMENT_NOT_RENEWABLE", "已有冻结或提交事实，须按原提交或恢复流程处理");
    }

    private void checkTokenVersions(String token, PdaSessions.Principal principal) {
        try {
            var pieces = token.split("\\.");
            var claims = mapper.readTree(Base64.getUrlDecoder().decode(pieces[1]));
            if (!principal.userAuthVersion().equals(claims.path("userAuthVersion").asText())
                || !principal.deviceVersion().equals(claims.path("deviceVersion").asText()))
                throw new BusinessException(409, "OFFLINE_AUTH_REAUTHORIZATION_REQUIRED", "人员或设备授权版本已变化，不能直接续期");
        } catch (BusinessException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Stored authorization is invalid", error); }
    }

    private ObjectNode claims(AssignmentStore.View current, PdaSessions.Principal principal, UUID authorizationId,
        UUID previousId, long sequence, AssignmentStore.Publication publication, Instant issued, Instant expires) {
        return mapper.createObjectNode().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-offline")
            .put("jti", authorizationId.toString()).put("sub", principal.userId().toString())
            .put("deviceId", principal.deviceId().toString()).put("warehouseId", principal.warehouseId().toString())
            .put("assignmentId", current.id()).put("orderId", current.orderId()).put("revision", current.revision())
            .put("assignmentEpoch", current.epoch()).put("serverRecoveryEpoch", current.serverRecoveryEpoch())
            .put("inventorySnapshotId", current.inventorySnapshotId()).put("policyVersion", publication.policyVersion())
            .put("autoDeductInventory", publication.autoDeduct()).put("planSha256", current.planSha256())
            .put("inventorySha256", current.inventorySha256()).put("startOrderScanSeq", current.startOrderScanSeq())
            .put("allocationVersion", current.allocationVersion()).put("userAuthVersion", principal.userAuthVersion())
            .put("deviceVersion", principal.deviceVersion()).put("scope", "pda:scan")
            .put("authorizationSequence", Long.toString(sequence)).put("renewedFromAuthorizationId", previousId.toString())
            .put("iat", issued.getEpochSecond()).put("exp", expires.getEpochSecond());
    }

    public record Context(String recoveryEpoch, String key, String requestId) { }
    public record Command(@NotBlank String authorizationId, @NotBlank String assignmentEpoch, @NotBlank String orderId,
        @NotNull @Min(1) Integer revision, @NotNull @Min(0) Integer pendingInputCount) { }
    private record RequestBody(UUID assignmentId, UUID deviceId, UUID warehouseId, Command command) { }
    private record OrderState(String status, int revision) { }
    private record RenewalEvent(UUID assignmentId, String orderId, UUID previousAuthorizationId, UUID authorizationId, long sequence) { }
}
