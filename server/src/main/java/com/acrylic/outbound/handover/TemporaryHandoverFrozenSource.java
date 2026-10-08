package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates an activated receiving round's local body without accepting it as business input. */
@Component
public final class TemporaryHandoverFrozenSource {
    private final TemporaryHandoverIntakeFacts facts;
    private final TemporaryHandoverPayload payloads;
    private final TemporaryHandoverRegistration registration;
    private final TemporaryHandoverTarget target;
    private final JdbcTemplate jdbc;

    TemporaryHandoverFrozenSource(TemporaryHandoverIntakeFacts facts,TemporaryHandoverPayload payloads,
            TemporaryHandoverRegistration registration,TemporaryHandoverTarget target,JdbcTemplate jdbc) {
        this.facts=facts;this.payloads=payloads;this.registration=registration;this.target=target;this.jdbc=jdbc;
    }

    public JsonNode require(SubmissionStore.Head head,JsonNode body) {
        return checked(head,body).predecessor();
    }

    TemporaryHandoverPayload.Checked checked(SubmissionStore.Head head,JsonNode body) {
        var source=source(head,body);var checked=payloads.validate(source,head,body);manifest(head,body);
        if("RECEIVING".equals(head.status())) {
            registration.requireOpen(source.ticket());target.require(head,checked.plan(),false);
        } else if("ABORTED".equals(head.status())) {
            var receipt=head.receipt()==null?null:parse(head.receipt());
            if(receipt==null || !receipt.path("payloadPresent").isBoolean() || receipt.path("payloadPresent").asBoolean()
                || !receipt.path("payloadSha256").isNull() || !sha(body).equals(receipt.path("localFrozenPayloadSha256").asText()))throw changed();
        } else throw changed();
        return checked;
    }

    TemporaryHandoverIntakeFacts.Facts source(SubmissionStore.Head head,JsonNode body) {
        var request=TemporaryHandoverControl.read(body);
        equal(request.control(),head.control(),"独立冻结原控制头不符");
        equal(request.predecessor(),facts.reference(head.control().id()),"独立冻结登记引用不符");
        var source=facts.read(request);
        equal(source.ticket().targetUserId(),head.actor(),"独立冻结操作人不符");
        equal(source.warehouse(),head.warehouse(),"独立冻结仓库不符");
        if(head.order()==null || head.control().round()<2)throw changed();
        return source;
    }

    private void manifest(SubmissionStore.Head head,JsonNode body) {
        var rows=jdbc.queryForList("""
            SELECT u.payload_sha256,u.byte_count,u.chunk_count FROM submission_upload u
            WHERE u.submission_id=? AND NOT EXISTS(SELECT 1 FROM submission_payload p WHERE p.submission_id=u.submission_id)
            """,Ids.bytes(head.control().id()));
        if(rows.size()!=1)throw changed();var row=rows.getFirst();
        int bytes=canonical(body).getBytes(StandardCharsets.UTF_8).length;
        if(!sha(body).equals(row.get("payload_sha256")) || ((Number)row.get("byte_count")).intValue()!=bytes
            || ((Number)row.get("chunk_count")).intValue()!=(bytes+262143)/262144)throw changed();
    }

    static BusinessException changed() {
        return new BusinessException(409,"TEMP_HANDOVER_LOCAL_FROZEN_CONFLICT","独立冻结清单、原件或接收轮来源不可核验");
    }
}
