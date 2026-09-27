package com.acrylic.outbound.temporary.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

final class TemporaryDuplicateComparison {
    private TemporaryDuplicateComparison() { }
    static Candidate compare(Head head,List<Product> products,JsonNode context) {
        return compare(head,products,context,false);
    }
    /**
     * {@code linkAvailable} states whether the manual link action may be offered for this candidate: the command
     * surface must be enabled and every comparison blocker must be empty. An empty blocker list is still only a
     * comparison; the write service rechecks all conditions inside the link transaction.
     */
    static Candidate compare(Head head,List<Product> products,JsonNode context,boolean commandsEnabled) {
        var signals=new ArrayList<String>();var blockers=new ArrayList<String>();
        if(head.vehicleNo().equals(context.path("vehicleNo").asText()))signals.add("SAME_VEHICLE");
        var original=new ArrayList<Product>();context.path("products").forEach(p->original.add(new Product(p.path("deliveryCode").asText(),
            p.path("productId").asText(),p.path("model").asText(),p.path("name").asText(),p.path("plannedCount").asText())));
        if(products.stream().anyMatch(p->original.stream().anyMatch(t->p.productId().equals(t.productId()))))signals.add("SHARED_PRODUCT");
        if(!head.status().equals("READY"))blockers.add("TARGET_NOT_READY");
        if(head.hasAssignment())blockers.add("PDA_WORK_EXISTS");
        if(head.hasPcWork())blockers.add("PC_WORK_EXISTS");
        if(head.hasSubmission())blockers.add("SUBMISSION_EXISTS");
        if(head.hasResult())blockers.add("RESULT_EXISTS");
        if(!new HashSet<>(products).equals(new HashSet<>(original)))blockers.add("PLAN_DIFFERS");
        if(!head.inventorySnapshotId().equals(context.path("inventorySnapshotId").asText()))blockers.add("INVENTORY_VERSION_DIFFERS");
        if(!context.path("current").asBoolean() || !context.path("status").asText().equals("REVIEW_REQUIRED"))blockers.add("SOURCE_NOT_PENDING");
        // Even an empty blocker list is only a comparison; the write service must recheck all conditions.
        return new Candidate(head,List.copyOf(products),List.copyOf(signals),List.copyOf(blockers),commandsEnabled && blockers.isEmpty());
    }
}
