package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

final class HandoverListChecks {
    private final HandoverListFixture fixture;
    HandoverListChecks(HandoverListFixture fixture) { this.fixture = fixture; }

    void verify() throws Exception {
        var zero = fixture.page(20); assertPage(zero, 0, false);
        fixture.append(); assertPage(fixture.page(20), 1, false);
        for (int i = 1; i < 20; i++) fixture.append();
        assertPage(fixture.page(20), 20, false);
        fixture.append();
        var facts = new HandoverRollbackFacts(fixture.f, fixture.f.jdbc); var before = facts.snapshot();
        Files.writeString(fixture.attempt.resolve("list-before.json"), before.toPrettyString());
        var first = fixture.page(20); assertPage(first, 20, true);
        var second = SubmissionJson.parse(fixture.maker.get(fixture.path() + "&cursor=" + encode(first.path("nextCursor").asText()), 200));
        assertPage(second, 1, false);
        var actual = new ArrayList<String>();
        first.path("items").forEach(row -> actual.add(row.path("caseId").asText()));
        second.path("items").forEach(row -> actual.add(row.path("caseId").asText()));
        assertThat(actual).containsExactlyElementsOf(fixture.ids.stream().sorted(Comparator.reverseOrder()).toList());
        negative(first.path("nextCursor").asText());
        var after = facts.snapshot(); Files.writeString(fixture.attempt.resolve("list-after.json"), after.toPrettyString());
        assertThat(after).isEqualTo(before);
    }

    private void negative(String cursor) throws Exception {
        fixture.anonymous.get(fixture.path(), 401); fixture.denied.get(fixture.path(), 403);
        fixture.maker.get(fixture.path().replace(fixture.f.warehouse.toString(), UUID.randomUUID().toString()), 404);
        fixture.maker.get(fixture.path().replace(fixture.f.warehouse.toString(), fixture.otherWarehouse.toString()), 404);
        fixture.maker.get(fixture.path().replace(fixture.f.order.toString(), UUID.randomUUID().toString()), 404);
        for (String query : List.of("pageSize=0", "pageSize=101", "pageSize=no", "cursor=bad!", "cursor="))
            fixture.maker.get(fixture.path() + "&" + query, 400);
        fixture.maker.get(fixture.path().replace(fixture.f.warehouse.toString(), fixture.otherWarehouse.toString()) + "&cursor=" + encode(cursor), 400);
        fixture.maker.get(fixture.path().replace(fixture.f.order.toString(), UUID.randomUUID().toString()) + "&cursor=" + encode(cursor), 400);
        String id = fixture.page(1).path("items").get(0).path("caseId").asText();
        var detail = SubmissionJson.parse(fixture.maker.get("/handovers/" + id + "/context?warehouseId=" + fixture.f.warehouse + "&commandType=APPROVE", 200));
        assertThat(detail.path("context").path("canonicalOrderId").asText()).isEqualTo(fixture.f.order.toString());
        assertThat(detail.path("originalCommand").path("reason").asText()).isEqualTo("合成交接查询验收");
    }

    private void assertPage(JsonNode page, int count, boolean more) {
        assertThat(page.path("warehouseId").asText()).isEqualTo(fixture.f.warehouse.toString());
        assertThat(page.path("orderId").asText()).isEqualTo(fixture.f.order.toString());
        assertThat(page.path("items").size()).isEqualTo(count);
        assertThat(page.path("hasMore").asBoolean()).isEqualTo(more);
        assertThat(page.path("nextCursor").isNull()).isEqualTo(!more);
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
