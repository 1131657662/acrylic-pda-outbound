package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** No stock locks or mutations: other-source facts never reserve the current TEMP's K. */
@Component
public class UnsubmittedSourceFacts {
    private final UnsubmittedSourceObjects objects;
    private final UnsubmittedSourceAuthority authority;
    private final UnsubmittedSourceAudits audits;

    public UnsubmittedSourceFacts(UnsubmittedSourceObjects objects, UnsubmittedSourceAuthority authority,
        UnsubmittedSourceAudits audits) {
        this.objects = objects; this.authority = authority; this.audits = audits;
    }

    Map<UUID, JsonNode> validate(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items, long through) {
        try {
            var captures = read(binding, items, "CAPTURE");
            var scans = read(binding, items, "SCAN");
            require(captures.keySet().containsAll(scans.keySet()), "其他来源明细缺原输入");
            var history = audits.read(binding, through, captures.keySet());
            var preparations = new HashMap<String, UnsubmittedSourcePreparation.Parts>();
            for (var entry : captures.entrySet()) UnsubmittedSourceRecords.validate(entry.getValue(), scans.get(entry.getKey()),
                history.getOrDefault(entry.getKey(), List.of()), authority.resolve(binding, entry.getValue(), preparations));
            return scans;
        } catch (RuntimeException invalid) {
            throw new BusinessException(409, "E2A_OTHER_UNSUBMITTED_UNVERIFIABLE", "其他未提交来源原授权、库存或审计不可核验");
        }
    }

    private Map<UUID, JsonNode> read(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items, String kind) {
        var result = new HashMap<UUID, JsonNode>();
        for (var item : items) {
            if (!kind.equals(item.itemKind())) continue;
            var original = objects.read(binding, item);
            require(result.put(item.originalRecordId(), original) == null, "其他来源记录重复");
        }
        return result;
    }
}
