package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.TemporaryConfirmationSource;
import com.acrylic.outbound.temporary.TemporaryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Independent confirmation replays the accepted handover facts before the existing accounting operation. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryHandoverConfirmationSource {
    private final SubmissionStore submissions;private final SubmissionFiles files;private final TemporaryHandoverIntakeFacts facts;
    private final TemporaryHandoverPayload payload;private final TemporaryHandoverSubmissionOwners owners;
    private final TemporaryHandoverTarget targets;private final JdbcTemplate jdbc;
    TemporaryHandoverConfirmationSource(SubmissionStore submissions,SubmissionFiles files,TemporaryHandoverIntakeFacts facts,
        TemporaryHandoverPayload payload,TemporaryHandoverSubmissionOwners owners,TemporaryHandoverTarget targets,JdbcTemplate jdbc) {
        this.submissions=submissions;this.files=files;this.facts=facts;this.payload=payload;this.owners=owners;this.targets=targets;this.jdbc=jdbc;
    }
    public boolean contains(java.util.UUID submission) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_submission WHERE submission_id=?",Integer.class,Ids.bytes(submission))==1;
    }
    public Checked read(SubmissionStore.Head head) {
        var hash=submissions.payloadHash(head.control().id());if(hash==null)throw TemporaryHandoverIntakeFacts.changed();
        var body=parse(files.read(hash));equal(sha(body),hash,"交接受理原文损坏");var request=TemporaryHandoverControl.read(body);
        equal(request.control(),head.control(),"交接受理控制身份不符");
        equal(request.predecessor(),facts.reference(head.control().id()),"交接不可变引用不符");
        var source=facts.read(request);var checked=payload.validate(source,head,body);boolean linked=targets.require(head,checked.plan(),true);
        owners.require(source,head,checked.snapshot());
        return new Checked(new TemporaryConfirmationSource.Value(head,body,checked.snapshot(),linked),checked.plan().authorization());
    }
    public record Checked(TemporaryConfirmationSource.Value value,TemporaryStore.Stored authorization) { }
}
