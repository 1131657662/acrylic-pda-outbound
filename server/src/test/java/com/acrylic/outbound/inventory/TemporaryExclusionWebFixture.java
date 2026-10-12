package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;

/** Fresh synthetic data for Chrome; commands still traverse the real HTTP surface. */
public final class TemporaryExclusionWebFixture {
    private TemporaryExclusionWebFixture() { }
    public static void prepare(WebApplicationContext context,Path attempt) throws Exception {
        var f=new TemporaryExclusionHttpFixture(context);
        f.source.only(f.supervisor,List.of("temporary_order:link","temporary_order:confirm","submission:review","order:read"));
        var input=SubmissionJson.object().put("purpose","SYNTHETIC_EXCLUSION_WEB")
            .put("warehouseId",f.source.source.f.web.identity().warehouse().toString())
            .put("submissionId",f.source.source.id()).put("cancelledId",f.cancelled()).put("unrelatedId",f.unrelated());
        input.putObject("supervisor").put("username",f.supervisor.getUsername()).put("password","Test-password-123");
        input.putObject("original").put("username",f.source.operator().getUsername()).put("password","Test-password-123");
        Files.createDirectories(attempt.resolve("private"));
        Files.writeString(attempt.resolve("private/exclusion-web-input.json"),SubmissionJson.canonical(input));
    }
}
