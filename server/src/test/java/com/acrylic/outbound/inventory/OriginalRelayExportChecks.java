package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** A relay-created result must be consumable through the existing authenticated export route. */
final class OriginalRelayExportChecks {
    private OriginalRelayExportChecks() { }
    static void verify(WebApplicationContext context,OriginalRelayHttpFixture f,Path evidence) throws Exception {
        var scope=new ExportModels.Scope(f.original.web.identity().user().id(),f.original.web.identity().warehouse(),
            UUID.fromString(f.original.order));
        var wire=CorrectionExportHttpClient.login(context,scope);
        var consumer=new ExportNetJobConsumer(context,wire,evidence.resolve("new-relay-export"));
        UUID key=UUID.randomUUID();var body=SubmissionJson.object().put("format","SCAN_XLSX").put("basis","ORIGINAL_CONFIRMED");
        UUID job=consumer.create("/orders/"+f.original.order+"/exports",body,key);
        var order=consumer.snapshot(job).orders().getFirst();
        assertThat(order.resultVersion()).isEqualTo(1);assertThat(order.originalLines()).hasSize(2);
        assertThat(order.originalLines()).extracting(line->line.orderScanSeq()).containsExactly(1L,2L);
        assertThat(order.originalLines()).allSatisfy(line->
            assertThat(line.originDeviceId()).isEqualTo(f.original.device.id()));
        consumer.download(job,"new-relay-scans.xlsx","scan-records-v1-");
        assertThat(consumer.create("/orders/"+f.original.order+"/exports",body,key)).isEqualTo(job);
        assertThat(f.original.count("inventory_ledger")).isEqualTo(2);
        assertThat(f.original.count("order_result_version")).isEqualTo(1);
    }
}
