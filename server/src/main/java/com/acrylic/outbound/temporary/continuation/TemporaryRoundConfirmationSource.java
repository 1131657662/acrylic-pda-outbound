package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.TemporaryConfirmationSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates the explicit new round before any existing confirmation operation can account for it. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundConfirmationSource {
    private final SubmissionStore submissions;private final SubmissionFiles files;private final TemporaryRoundFacts facts;
    private final TemporaryRoundPayload payload;private final TemporaryRoundSelection selection;private final JdbcTemplate jdbc;
    private final TemporaryRoundConfirmationOwners owners;
    public TemporaryRoundConfirmationSource(SubmissionStore submissions,SubmissionFiles files,TemporaryRoundFacts facts,
        TemporaryRoundPayload payload,TemporaryRoundSelection selection,JdbcTemplate jdbc,TemporaryRoundConfirmationOwners owners) {
        this.submissions=submissions;this.files=files;this.facts=facts;this.payload=payload;this.selection=selection;this.jdbc=jdbc;this.owners=owners;
    }
    public Checked read(SubmissionStore.Head head) {
        String hash=submissions.payloadHash(head.control().id());if(hash==null)throw TemporaryRoundFacts.conflict("TEMP_CONTINUATION_PAYLOAD_UNAVAILABLE");
        var body=parse(files.read(hash));equal(sha(body),hash,"续作受理原文损坏");
        var request=TemporaryRoundControl.read(body);equal(request.control(),head.control(),"续作受理控制身份不符");
        equal(request.predecessor(),facts.reference(head.control().id()),"续作不可变绑定不符");
        jdbc.queryForList("SELECT id FROM temporary_source_continuation WHERE id=? FOR UPDATE",Ids.bytes(request.predecessor().ticket()));
        var source=facts.read(request,new SubmissionAccess.Actor(head.actor(),head.warehouse(),""));
        if(!"ACTIVATED".equals(source.status()))throw TemporaryRoundFacts.conflict("CONTINUATION_NOT_ACTIVATED");
        var input=new TemporaryRoundPayload.Input(request,source,head,facts.retained(request,source));
        var checked=payload.validate(input,body);selection.requireTarget(head,source,checked.plan());
        owners.require(input,checked.snapshot());
        return new Checked(new TemporaryConfirmationSource.Value(head,body,checked.snapshot(),false),checked.plan().authorization());
    }
    public record Checked(TemporaryConfirmationSource.Value value,com.acrylic.outbound.temporary.TemporaryStore.Stored authorization) { }
}
