package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A supervisor observes one consistent set. No physical verification or association is inferred. */
@Service
public class RelayIndependentReconciliation {
    private final RelayOriginals originals;private final RelayIndependentReview reviews;
    private final RelayIndependentCandidateQuery candidates;private final RelayIndependentInventory inventory;private final RelayIndependentPeers peers;
    public RelayIndependentReconciliation(RelayOriginals originals,RelayIndependentReview reviews,RelayIndependentCandidateQuery candidates,
        RelayIndependentInventory inventory,RelayIndependentPeers peers) {
        this.originals=originals;this.reviews=reviews;this.candidates=candidates;this.inventory=inventory;this.peers=peers;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public JsonNode read(UUID warehouse,UUID id,RelayIndependentReviewPage.Query query) {
        return RelayIndependentReviewPage.read(full(warehouse,id),query);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public ObjectNode snapshot(UUID warehouse,UUID id) { return full(warehouse,id); }
    private ObjectNode full(UUID warehouse,UUID id) {
        var review=reviews.read(warehouse,id);eligible(review);var selected=originals.read(warehouse,id);var bundle=selected.bundle();
        var full=object();full.set("review",review);var original=full.putObject("original").put("warehouseId",warehouse.toString())
            .put("relayId",id.toString()).put("exportId",bundle.exportId().toString()).put("archiveSha256",bundle.archiveSha256())
            .put("taskId",bundle.original().localOrder().toString()).put("deviceId",bundle.original().device().toString())
            .put("operatorId",bundle.original().user().toString()).put("closureSha256",sha(bundle.closure()));
        original.set("task",bundle.independent().path("task"));original.set("closure",bundle.closure());
        var orders=candidates.read(bundle);full.set("candidates",orders);var stock=inventory.read(bundle);var other=peers.read(bundle);
        var stockRows=full.putArray("inventory");stock.forEach(stockRows::add);var peerRows=full.putArray("peers");other.forEach(peerRows::add);
        var summary=full.putObject("summary").put("candidateTotal",text(orders,"total")).put("activeTotal",Integer.toString(stock.size()))
            .put("inventoryBlocked",Long.toString(stock.stream().filter(row->!row.path("blockers").isEmpty()).count()))
            .put("peerTaskTotal",Integer.toString(other.size())).put("crossTaskConflicts",Long.toString(other.stream().filter(row->row.path("crossTaskConflict").asBoolean()).count()))
            .put("closureDivergences",Long.toString(other.stream().filter(row->row.path("closureDivergence").asBoolean()).count()))
            .put("fromDate",text(orders,"fromDate")).put("throughDate",text(orders,"throughDate"));
        summary.put("onlyReceivedOriginalsObserved",true).put("physicalVerified",false).put("manuallyAssociated",false);
        return full;
    }
    static void eligible(JsonNode review) {
        if(!text(review,"sourceTrust").equals("TRUSTED") || !text(review,"authorizationState").equals("VERIFIED"))
            throw new BusinessException(409,"INDEPENDENT_ORIGIN_UNVERIFIED","原登记设备钥或历史授权尚未核验，原件保留");
        if(!review.path("completeBoundaryVerified").asBoolean())
            throw new BusinessException(409,"INDEPENDENT_COMPLETE_REQUIRED","原独立任务尚无完整结束边界，继续保全原件");
    }
}
