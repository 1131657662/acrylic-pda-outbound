package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.continuation.DispositionProofs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Exact coverage of the original effective scans; prior dispositions and all raw inputs stay in the recovery body. */
@Component
final class HandoverChecklist {
    JsonNode dispositions(JsonNode body, JsonNode lines) {
        if (!lines.isArray()) throw invalid();
        var scans = new LinkedHashMap<UUID, JsonNode>();
        for (var scan : DispositionProofs.effective(body)) {
            if (scans.put(uuid(scan, "baleId"), scan) != null) throw invalid();
        }
        if (lines.size() != scans.size()) throw mismatch();
        var command = object(); var rows = command.putArray("dispositions"); var seen = new HashSet<UUID>();
        for (var line : lines) {
            var bale = uuid(line, "baleId"); var scan = scans.get(bale);
            if (scan == null || !seen.add(bale)) throw mismatch();
            if (!text(line, "deliveryCode").equals(text(scan, "deliveryCode"))
                    || number(line, "dispatchCycle") != number(scan, "expectedDispatchCycle")) throw mismatch();
            rows.add(disposition(line, scan));
        }
        return command;
    }

    private ObjectNode disposition(JsonNode line, JsonNode scan) {
        var fields = new HashSet<String>(); line.fieldNames().forEachRemaining(fields::add);
        String state = text(line, "state");
        var required = new HashSet<>(Set.of("baleId", "deliveryCode", "state", "dispatchCycle"));
        if (state.equals("REMOVED")) required.add("approvedEvidenceId");
        if (!fields.equals(required) || !Set.of("RETAINED", "REMOVED").contains(state)) throw invalid();
        var row = object().put("barcode", text(scan, "barcode")).put("expectedDispatchCycle", text(line, "dispatchCycle"));
        if (state.equals("RETAINED")) return row.put("action", "RETAIN");
        // The action is populated only after reading the already-approved physical disposition.
        return row.put("approvedEvidenceId", uuid(line, "approvedEvidenceId").toString());
    }

    void compareStored(List<Map<String, Object>> stored, JsonNode lines, boolean approved) {
        if (stored.isEmpty() && !approved) return;
        if (stored.size() != lines.size()) throw mismatch();
        for (int i = 0; i < stored.size(); i++) {
            var row = stored.get(i); var line = lines.get(i);
            if (!uuid(line, "baleId").toString().equals(row.get("bale_id"))
                    || !text(line, "deliveryCode").equals(row.get("delivery_code"))
                    || !text(line, "state").equals(row.get("state"))
                    || number(line, "dispatchCycle") != ((Number) row.get("dispatch_cycle")).longValue()
                    || i != ((Number) row.get("item_no")).intValue()) throw mismatch();
        }
    }

    private BusinessException invalid() { return new BusinessException(400, "HANDOVER_CHECKLIST_REQUIRED", "逐包清单字段或状态无效，移除须引用已批准实物凭据"); }
    private BusinessException mismatch() { return new BusinessException(409, "HANDOVER_CHECKLIST_MISMATCH", "逐包清单与完整原包集合或已核验清单不一致"); }
}
