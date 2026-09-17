package com.acrylic.outbound.orders;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.policies.PolicyStore;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderPublishing {
    private final Access access;
    private final PolicyStore policies;
    private final OrderPublicationStore publications;
    private final OrderQueries queries;
    public OrderPublishing(Access access,PolicyStore policies,OrderPublicationStore publications,OrderQueries queries) {
        this.access=access;this.policies=policies;this.publications=publications;this.queries=queries;
    }
    public PolicyStore.Policy prepare(UUID warehouse,String expectedPolicyVersion) {
        access.require("order:publish",warehouse);
        return policies.require(warehouse,expectedPolicyVersion);
    }
    public void publish(Work work,PolicyStore.Policy policy) {
        publications.markReady(work.order());
        publications.save(work,policy,queries.detail(work.warehouse(),work.order(),null));
    }
    public void cancel(Work work) {
        publications.cancelled(work,queries.detail(work.warehouse(),work.order(),null));
    }
    public record Work(UUID warehouse,UUID order,UUID actor,String epoch,String reason) { }
}
