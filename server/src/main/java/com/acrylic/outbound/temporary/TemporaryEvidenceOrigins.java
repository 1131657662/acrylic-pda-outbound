package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Qualification boundaries come from verified predecessor prefixes, never a row's claimed source. */
public final class TemporaryEvidenceOrigins {
    private final List<Boundary> boundaries;
    private TemporaryEvidenceOrigins(List<Boundary> boundaries) { this.boundaries=List.copyOf(boundaries); }
    public TemporaryEvidenceOrigins(Origin current,Origin previous,int captures,int events) {
        this(previous==null?List.of(new Boundary(0,0,current)):
            List.of(new Boundary(0,0,previous),new Boundary(captures,events,current)));
    }
    public static TemporaryEvidenceOrigins initial(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        return new TemporaryEvidenceOrigins(List.of(new Boundary(0,0,new Origin(head.control().source(),plan.authorization()))));
    }
    public TemporaryEvidenceOrigins append(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan,JsonNode previous) {
        var result=new ArrayList<>(boundaries);var last=result.getLast();
        int captures=array(previous,"captures").size(),events=array(previous,"events").size();
        require(captures>=last.captures() && events>last.events(),"续作来源边界倒退");
        require(boundaries.stream().noneMatch(b->b.origin().authorization().id().equals(plan.authorization().id())),"续作资格重复");
        result.add(new Boundary(captures,events,new Origin(head.control().source(),plan.authorization())));
        return new TemporaryEvidenceOrigins(result);
    }
    public Origin capture(JsonNode row,int index) { return at(index,true); }
    public Origin event(JsonNode row,int index) { return at(index,false); }
    public TemporaryStore.Stored authorization(JsonNode row) {
        return boundaries.stream().map(Boundary::origin).map(Origin::authorization)
            .filter(a->a.id().toString().equals(text(row,"authorizationId"))).findFirst()
            .orElseThrow(()->new IllegalStateException("Validated capture has no qualification"));
    }
    private Origin at(int index,boolean capture) {
        for(int i=boundaries.size()-1;i>=0;i--) {
            var b=boundaries.get(i);if(index>=(capture?b.captures():b.events()))return b.origin();
        }
        throw new IllegalArgumentException("Negative evidence index");
    }
    private record Boundary(int captures,int events,Origin origin) { }
    public record Origin(JsonNode source,TemporaryStore.Stored authorization) { }
}
