package com.acrylic.outbound.exports;

import com.acrylic.outbound.reports.OrderSnapshot;
import com.acrylic.outbound.temporary.*;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.exports.ExportFacts.check;

/** Reconciles the fixed TEMP plan and original captures, without checking current permissions. */
@Component
public class TemporaryExportPlan {
    private final TemporarySubmissionPlan plans;private final TemporarySubmissionEvidence evidence;
    private final TemporarySubmissionSnapshot snapshots;
    public TemporaryExportPlan(TemporarySubmissionPlan plans,TemporarySubmissionEvidence evidence,TemporarySubmissionSnapshot snapshots) {
        this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
    }
    public void validate(TemporaryExportEvidence.Value original,ExportFacts.Plan stored,List<OrderSnapshot.Product> products) {
        var body=original.body();var plan=plans.validate(original.head(),body);
        evidence.validate(original.head(),plan,body);snapshots.validate(plan,body);
        var expected=plan.lines().stream().map(p->new OrderSnapshot.Product(p.code(),text(p.mapping(),"name"),
                text(p.mapping(),"model"),p.count())).toList();
        check(products.equals(expected),"TEMP原产品计划与持久化计划不符");
        check(stored.header().equals(new OrderSnapshot.Header(text(plan.body(),"vehicleNo"),text(plan.body(),"containerNo"),"")),
                "TEMP原车柜与持久化计划不符");
    }
}
