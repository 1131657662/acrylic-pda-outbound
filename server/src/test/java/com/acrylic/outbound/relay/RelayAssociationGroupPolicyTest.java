package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class RelayAssociationGroupPolicyTest {
    private record Group(RelayAssociationFixture main,RelayAssociationFixture other) {
        List<JsonNode> contexts() { return List.of(main.context,other.context); }
    }
    private Group group() {
        var a=new RelayAssociationFixture();var b=new RelayAssociationFixture();
        a.candidate().put("plannedTotal","2");((ObjectNode)a.candidate().path("products").get(0)).put("plannedCount","2");
        ((ArrayNode)b.context.path("candidates").path("items")).set(0,a.candidate().deepCopy());
        for(var f:List.of(a,b)) {
            var row=(ObjectNode)f.context.path("inventory").get(0);
            ((ObjectNode)row.path("original")).put("barcode",UUID.randomUUID().toString());
            ((ObjectNode)row.path("server")).put("snapshotId",a.snapshot.toString()).put("baleId",UUID.randomUUID().toString());
            f.refresh();
        }
        var task=a.request.putArray("additionalTasks").addObject().put("relayId",b.physical.scope.relay().toString())
            .put("contextSha256",sha(b.context)).put("physicalCheckId",b.check.toString());task.putArray("candidateExclusions");
        return new Group(a,b);
    }
    @Test void explicitDistinctTasksSatisfyCombinedPlanWithoutMutatingOriginals() {
        var g=group();var before=g.contexts().stream().map(JsonNode::deepCopy).toList();
        RelayAssociationPolicy.validate(g.main.request);RelayAssociationGroupPolicy.distinct(g.contexts());
        RelayAssociationPlan.completeGroup(g.main.request,g.main.candidate(),g.contexts());assertThat(g.contexts()).isEqualTo(before);
    }
    @Test void duplicateTaskOrBarcodeOrServerBaleCannotCountTwice() {
        for(String kind:List.of("task","barcode","bale")) {
            var g=group();var a=g.main.context;var b=g.other.context;
            if(kind.equals("task"))b.set("original",a.path("original").deepCopy());
            else if(kind.equals("barcode"))((ObjectNode)b.path("inventory").get(0).path("original")).put("barcode",text(a.path("inventory").get(0).path("original"),"barcode"));
            else ((ObjectNode)b.path("inventory").get(0).path("server")).put("baleId",text(a.path("inventory").get(0).path("server"),"baleId"));
            assertThatThrownBy(()->RelayAssociationGroupPolicy.distinct(g.contexts())).isInstanceOf(BusinessException.class);
        }
    }
    @Test void missingEmptyDuplicateOrTooManyExplicitMembersFailValidation() {
        for(int size:new int[]{0,2,20}) {
            var g=group();var tasks=(ArrayNode)g.main.request.path("additionalTasks");var member=tasks.get(0).deepCopy();tasks.removeAll();
            for(int i=0;i<size;i++)tasks.add(member.deepCopy());
            assertThatThrownBy(()->RelayAssociationPolicy.validate(g.main.request)).isInstanceOf(BusinessException.class);
        }
        var g=group();((ObjectNode)g.main.request.path("additionalTasks").get(0)).put("releaseAllowed",true);
        assertThatThrownBy(()->RelayAssociationPolicy.validate(g.main.request)).isInstanceOf(BusinessException.class);
    }
    @Test void combinedShortageRequiresExactApprovalAcrossAllOriginalPlans() {
        var g=group();((ObjectNode)g.other.context.path("original").path("closure")).put("plannedTotal","3").put("shortfall","2").put("shortfallReason","合成原短装");
        g.main.candidate().put("plannedTotal","3");((ObjectNode)g.main.candidate().path("products").get(0)).put("plannedCount","3");
        assertThatThrownBy(()->RelayAssociationPlan.completeGroup(g.main.request,g.main.candidate(),g.contexts())).isInstanceOf(BusinessException.class);
        g.main.request.putObject("shortfallApproval").put("originalShortfall","2").put("targetShortfall","1").put("reason","合成显式全组批准");
        assertThatCode(()->RelayAssociationPlan.completeGroup(g.main.request,g.main.candidate(),g.contexts())).doesNotThrowAnyException();
        ((ObjectNode)g.main.request.path("shortfallApproval")).put("originalShortfall","1");
        assertThatThrownBy(()->RelayAssociationPlan.completeGroup(g.main.request,g.main.candidate(),g.contexts())).isInstanceOf(BusinessException.class);
    }
    @Test void aggregateOverloadOrDifferentProductIsNotAValidShortage() {
        for(String kind:List.of("overload","product","snapshot")) {
            var g=group();
            if(kind.equals("overload")) { g.main.candidate().put("plannedTotal","1");((ObjectNode)g.main.candidate().path("products").get(0)).put("plannedCount","1"); }
            if(kind.equals("product"))((ObjectNode)g.other.context.path("inventory").get(0).path("original")).put("model","other");
            if(kind.equals("snapshot"))((ObjectNode)g.other.context.path("inventory").get(0).path("server")).put("snapshotId",UUID.randomUUID().toString());
            assertThatThrownBy(()->RelayAssociationPlan.completeGroup(g.main.request,g.main.candidate(),g.contexts())).isInstanceOf(BusinessException.class);
        }
    }
    @Test void multitaskGateOnlyBlocksNewGroupIntentsByDefault() {
        var gate=new RelayAssociationGroups(null,null,null,null,null,null,false);
        assertThatCode(()->gate.require(new RelayAssociationFixture().request)).doesNotThrowAnyException();
        assertThatThrownBy(()->gate.require(group().main.request)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("INDEPENDENT_MULTITASK_DISABLED"));
    }
}
