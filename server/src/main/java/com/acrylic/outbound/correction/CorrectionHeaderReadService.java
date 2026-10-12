package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** One order's verified scalar header and bounded original documents for the maker/checker page. */
@Service
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class CorrectionHeaderReadService {
    private final JdbcTemplate jdbc;
    private final CorrectionCommand.Identity identity;
    private final CorrectionStore facts;
    private final CorrectionHeaderEvidence headers;
    private final CorrectionReadReceipt verifier;
    private final CorrectionReadStore originals;

    CorrectionHeaderReadService(JdbcTemplate jdbc, CorrectionCommand.Identity identity, CorrectionStore facts,
        CorrectionHeaderEvidence headers, CorrectionReadReceipt verifier, CorrectionReadStore originals) {
        this.jdbc=jdbc; this.identity=identity; this.facts=facts; this.headers=headers; this.verifier=verifier; this.originals=originals;
    }

    public JsonNode read(Query query) {
        var actor = identity.current(query.warehouse(), query.review());
        var order = facts.orderFacts(query.order());
        if (!actor.warehouse().toString().equals(order.path("warehouseId").asText())) throw CorrectionCommand.missing();
        var result = SubmissionJson.object(); result.set("context", order);
        result.set("header", headers.normalize(query.order(), SubmissionJson.parse(facts.currentHeaderSnapshot(query.order()))));
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM correction_document WHERE order_id=? AND type='HEADER_CORRECTION'"
            + " AND (? IS NULL OR id<UUID_TO_BIN(?)) ORDER BY id DESC LIMIT 101",
            Ids.bytes(query.order()), query.before()==null?null:query.before().toString(), query.before()==null?null:query.before().toString());
        var items = result.putArray("items");
        rows.stream().limit(100).forEach(row -> items.add(document(UUID.fromString(row.get("id").toString()))));
        if (rows.size() > 100) result.put("nextCursor", rows.get(99).get("id").toString());
        else result.putNull("nextCursor");
        result.set("capabilities", CorrectionReceiptCodec.capabilities()); return result;
    }

    private JsonNode document(UUID id) {
        var context = facts.documentFacts(id);
        var original = verifier.verify(originals.originalCreate(id));
        if (!context.path("documentSha256").equals(original.receipt().path("commandSha256"))
            || !context.path("requestedBy").equals(original.receipt().path("actor").path("principalId"))
            || !context.path("orderId").equals(original.receipt().path("orderId"))) throw CorrectionCommand.corrupt();
        var item = SubmissionJson.object(); item.set("context", context);
        item.set("headerPatch", original.command().path("headerPatch")); item.set("reason", original.command().path("reason"));
        return item;
    }

    public record Query(UUID warehouse, UUID order, boolean review, UUID before) { }
}
