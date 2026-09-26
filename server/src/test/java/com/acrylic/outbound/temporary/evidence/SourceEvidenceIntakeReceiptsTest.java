package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeReceiptsTest {
    private static final byte[] REQUEST="{\"operation\":\"OPEN\"}".getBytes();
    private static final byte[] RESPONSE="{\"status\":\"RECEIVED\"}".getBytes();

    @Test void insertOpenWritesOnlyTheV35IntakeEnvelope() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var receipts=new SourceEvidenceIntakeReceipts(jdbc);var context=context();
        var receipt=new SourceEvidenceIntakeReceiptCodec.Receipt(context.originalKey(),RESPONSE,ImportFiles.sha256(RESPONSE),
            "offline-key","signed-token",Instant.parse("2026-09-23T12:34:56.123Z"));

        receipts.insertOpen(context,receipt);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(),args.capture());Object[] values=args.getValue();
        assertThat(sql.getValue()).contains("operation_kind,receipt_status,canonical_request_bytes,request_sha256")
            .contains("NULL,NULL,NULL");
        assertThat(values).hasSize(24);
        assertThat((byte[])values[0]).containsExactly(Ids.bytes(context.originalKey()));
        assertThat(values[1]).isEqualTo(1);assertThat((byte[])values[2]).containsExactly(Ids.bytes(context.principalId()));
        assertThat(values[3]).isEqualTo(E2aProtocol.ROUTE_SCOPE);assertThat((byte[])values[4]).containsExactly(Ids.bytes(context.originalKey()));
        assertThat((byte[])values[5]).containsExactly(Ids.bytes(context.currentSessionId()));assertThat((byte[])values[6]).containsExactly(Ids.bytes(context.warehouseId()));
        assertThat((byte[])values[7]).containsExactly(Ids.bytes(context.scopeId()));assertThat((byte[])values[8]).containsExactly(Ids.bytes(context.submissionId()));
        assertThat(values[9]).isEqualTo(2L);assertThat((byte[])values[10]).containsExactly(Ids.bytes(context.creatorDeviceId()));
        assertThat((byte[])values[11]).containsExactly(Ids.bytes(context.clientOrderId()));assertThat((byte[])values[12]).containsExactly(Ids.bytes(context.authorizationId()));
        assertThat((byte[])values[13]).containsExactly(Ids.bytes(context.originalOperatorId()));assertThat(values[14]).isEqualTo(3L);
        assertThat(values[15]).isEqualTo("OPEN");assertThat(values[16]).isEqualTo("RECEIVED");
        assertThat((byte[])values[17]).containsExactly(REQUEST);assertThat(values[18]).isEqualTo(context.requestSha256());
        assertThat((byte[])values[19]).containsExactly(RESPONSE);assertThat(values[20]).isEqualTo(ImportFiles.sha256(RESPONSE));
        assertThat(values[21]).isEqualTo("offline-key");assertThat(values[22]).isEqualTo("signed-token");
        assertThat(values[23]).isEqualTo(Timestamp.from(receipt.recordedAt()));
    }

    @Test void insertOpenIsMandatoryInsideTheCallerTransaction() throws NoSuchMethodException {
        var method=SourceEvidenceIntakeReceipts.class.getMethod("insertOpen",SourceEvidenceIntakeReceipts.OpenInsert.class,
            SourceEvidenceIntakeReceiptCodec.Receipt.class);
        var transaction=new AnnotationTransactionAttributeSource().getTransactionAttribute(method,SourceEvidenceIntakeReceipts.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
    }

    @Test void insertOpenRejectsPrincipalOperatorMismatch() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);var principal=UUID.randomUUID();
        var context=new SourceEvidenceIntakeReceipts.OpenInsert(principal,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),3,
            UUID.randomUUID(),"7","1",REQUEST,"0".repeat(64));
        var receipt=new SourceEvidenceIntakeReceiptCodec.Receipt(context.originalKey(),RESPONSE,ImportFiles.sha256(RESPONSE),
            "key","token",Instant.parse("2026-09-23T12:34:56.123Z"));

        assertThatThrownBy(() -> new SourceEvidenceIntakeReceipts(jdbc).insertOpen(context,receipt))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("OPEN_PRINCIPAL_OPERATOR");
    }

    private SourceEvidenceIntakeReceipts.OpenInsert context() {
        UUID principal=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        return new SourceEvidenceIntakeReceipts.OpenInsert(principal,UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
            UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
            UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"),2,UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
            UUID.fromString("11111111-1111-4111-8111-111111111111"),UUID.fromString("22222222-2222-4222-8222-222222222222"),
            principal,3,UUID.fromString("33333333-3333-4333-8333-333333333333"),"7","1",REQUEST,E2aProtocol.intakeSha256(REQUEST));
    }
}
