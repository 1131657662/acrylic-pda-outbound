package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakeReplayTest {
    private static final byte[] REQUEST="{\"operation\":\"OPEN\"}".getBytes(StandardCharsets.UTF_8);
    private static final String REQUEST_HASH=E2aProtocol.intakeSha256(REQUEST);
    private static final byte[] RESPONSE="{ \"status\" : \"RECEIVED\", \"signedReceipt\" : \"original\" }".getBytes(StandardCharsets.UTF_8);

    @Test void matchingKeyAndCanonicalRequestReturnsExactStoredResponseBytes() {
        var decision=SourceEvidenceIntakeReplay.evaluate(request(REQUEST),stored(REQUEST,REQUEST_HASH,RESPONSE));
        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(decision.originalResponseBytes()).isEqualTo(RESPONSE);
        assertThat(decision.trustStatus()).isEqualTo(SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        decision.originalResponseBytes()[0]=0;
        assertThat(decision.originalResponseBytes()).isEqualTo(RESPONSE);
    }

    @Test void sameKeyWithDifferentCanonicalRequestConflicts() {
        byte[] different="{\"operation\":\"PAGE\"}".getBytes(StandardCharsets.UTF_8);
        var decision=SourceEvidenceIntakeReplay.evaluate(request(different),
            stored(REQUEST,REQUEST_HASH,RESPONSE));
        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.CONFLICT);
        assertThat(decision.originalResponseBytes()).isNull();
    }

    @Test void corruptStoredRequestOrResponseDigestFailsClosed() {
        var badRequest=stored(REQUEST,"0".repeat(64),RESPONSE);
        var badResponse=stored(REQUEST,REQUEST_HASH,RESPONSE,"0".repeat(64));
        assertThat(SourceEvidenceIntakeReplay.evaluate(request(REQUEST),badRequest).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
        assertThat(SourceEvidenceIntakeReplay.evaluate(request(REQUEST),badResponse).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void storedNonObjectRequestFailsIntegrityEvenWhenItsDigestMatches() {
        byte[] scalar="null".getBytes(StandardCharsets.UTF_8);
        var tampered=stored(scalar,E2aProtocol.intakeSha256(scalar),RESPONSE);

        var decision=SourceEvidenceIntakeReplay.evaluate(request(REQUEST),tampered);

        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
        assertThat(decision.originalResponseBytes()).isNull();
    }

    @Test void canonicalizesEquivalentBodyAndRejectsMalformedJson() {
        byte[] nonCanonical="{ \"operation\" : \"OPEN\" }".getBytes(StandardCharsets.UTF_8);
        assertThat(SourceEvidenceIntakeReplay.canonicalize(nonCanonical).bytes()).isEqualTo(REQUEST);
        assertThatThrownBy(()->SourceEvidenceIntakeReplay.canonicalize("{".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test void canonicalizeRejectsDuplicateFieldsAsInvalidRequest() {
        byte[] duplicate="{\"operation\":\"OPEN\",\"operation\":\"PAGE\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(()->SourceEvidenceIntakeReplay.canonicalize(duplicate))
            .isInstanceOfSatisfying(BusinessException.class,error->{
                assertThat(error.status()).isEqualTo(400);
                assertThat(error.code()).isEqualTo("E2A_INTAKE_REQUEST_INVALID");
            });
    }

    @Test void canonicalizeRejectsNonObjectRoot() {
        byte[] scalar="null".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(()->SourceEvidenceIntakeReplay.canonicalize(scalar))
            .isInstanceOf(BusinessException.class);
    }

    @Test void canonicalEquivalentRequestReplaysAndDigestComesFromCanonicalBody() {
        byte[] spaced="{ \"operation\" : \"OPEN\" }".getBytes(StandardCharsets.UTF_8);
        var decision=SourceEvidenceIntakeReplay.evaluate(request(spaced),stored(REQUEST,REQUEST_HASH,RESPONSE));
        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(decision.originalResponseBytes()).isEqualTo(RESPONSE);
    }

    @Test void lookupUsesAuthenticatedPrincipalRouteAndOriginalKey() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row(stored(REQUEST,REQUEST_HASH,RESPONSE))));
        UUID principal=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID originalKey=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        var decision=new SourceEvidenceIntakeReceipts(jdbc).existing(principal,originalKey,REQUEST);
        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        var args=org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("WHERE principal_id=? AND route_scope=? AND original_key=?");
        Object[] values=args.getValue();
        assertThat(values).hasSize(3);
        assertThat((byte[])values[0]).containsExactly(Ids.bytes(principal));
        assertThat(values[1]).isEqualTo(E2aProtocol.ROUTE_SCOPE);
        assertThat((byte[])values[2]).containsExactly(Ids.bytes(originalKey));
    }

    @Test void missingNaturalKeyDoesNotImplyReplayOrSuccess() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

        var decision=new SourceEvidenceIntakeReceipts(jdbc).existing(UUID.randomUUID(),UUID.randomUUID(),REQUEST);

        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.NOT_FOUND);
        assertThat(decision.originalResponseBytes()).isNull();
    }

    @Test void lookupDeclaresReadCommittedReadOnlyTransaction() throws NoSuchMethodException {
        var method=SourceEvidenceIntakeReceipts.class.getMethod("existing",UUID.class,UUID.class,byte[].class);
        var transaction=new AnnotationTransactionAttributeSource()
            .getTransactionAttribute(method,SourceEvidenceIntakeReceipts.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.isReadOnly()).isTrue();
        assertThat(transaction.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Test void rollbackRecheckUsesIndependentReadCommittedReadOnlyTransaction() throws NoSuchMethodException {
        var method=SourceEvidenceIntakeReceipts.class.getMethod("recheckAfterRollback",
            SourceEvidenceIntakeReceipts.OriginalKey.class,byte[].class);
        var transaction=new AnnotationTransactionAttributeSource()
            .getTransactionAttribute(method,SourceEvidenceIntakeReceipts.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.isReadOnly()).isTrue();
        assertThat(transaction.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test void lookupDatabaseFailureDoesNotReplayOrMaskConstraintViolation() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var failure=new DataIntegrityViolationException("foreign key or check failure");
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenThrow(failure);
        var receipts=new SourceEvidenceIntakeReceipts(jdbc);

        assertThatThrownBy(()->receipts.existing(UUID.randomUUID(),UUID.randomUUID(),REQUEST))
            .isSameAs(failure);
    }

    private SourceEvidenceIntakeReplay.StoredReceipt stored(byte[] request,String requestHash,byte[] response) {
        return stored(request,requestHash,response,ImportFiles.sha256(response));
    }

    private SourceEvidenceIntakeReplay.CanonicalRequest request(byte[] requestBytes) {
        return SourceEvidenceIntakeReplay.canonicalize(requestBytes);
    }

    private SourceEvidenceIntakeReplay.StoredReceipt stored(byte[] request,String requestHash,byte[] response,String responseHash) {
        return new SourceEvidenceIntakeReplay.StoredReceipt(1,request,requestHash,response,responseHash);
    }

    private Map<String,Object> row(SourceEvidenceIntakeReplay.StoredReceipt receipt) {
        var row=new HashMap<String,Object>();
        row.put("receipt_version",receipt.receiptVersion());row.put("canonical_request_bytes",receipt.canonicalRequestBytes());
        row.put("request_sha256",receipt.requestSha256());row.put("original_response_bytes",receipt.originalResponseBytes());
        row.put("original_response_sha256",receipt.originalResponseSha256());return row;
    }
}
