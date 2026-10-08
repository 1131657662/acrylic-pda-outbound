package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** LOCAL original/checklist damage or lost supervisor permission must leave approval and preparation facts intact. */
final class HandoverTemporaryLocalBoundaryChecks {
    private final HandoverTemporaryStopFixture f;private final HandoverApprovalHttp reviewer;
    private final ObjectNode command;private final Path directory,original;
    private HandoverTemporaryLocalBoundaryChecks(HandoverTemporaryStopFixture f,HandoverApprovalHttp reviewer,ObjectNode command) throws Exception {
        this.f=f;this.reviewer=reviewer;this.command=command;
        directory=Files.createDirectory(f.directory.resolve("local-"+text(command,"commandType").toLowerCase()));
        String key=f.jdbc.queryForObject("SELECT file_key FROM source_evidence_object WHERE submission_id=? AND kind='LOCAL_FROZEN_PAYLOAD'",String.class,
            Ids.bytes(f.head.control().id()));original=f.directory.getParent().resolve("storage").resolve(key);
    }
    static void approval(HandoverTemporaryStopFixture f,HandoverApprovalHttp reviewer,ObjectNode command) throws Exception {
        var checks=new HandoverTemporaryLocalBoundaryChecks(f,reviewer,command);checks.files();checks.revoked();
        var smaller=command.deepCopy();smaller.withArray("checklist").remove(0);
        checks.refusal(smaller,409,"HANDOVER_CHECKLIST_MISMATCH","checklist");
    }
    static void preparation(HandoverTemporaryTargetFixture f,ObjectNode command) throws Exception {
        var checks=new HandoverTemporaryLocalBoundaryChecks(f.source,f.context.reviewer(),command);checks.files();checks.revoked();
    }
    private void files() throws Exception {
        Path saved=original.resolveSibling(original.getFileName()+".d06-local-retained");
        Files.move(original,saved);
        try {refusal(command,409,null,"missing");}finally {Files.move(saved,original);}
        byte[] bytes=Files.readAllBytes(original);
        try {
            byte[] corrupt=bytes.clone();corrupt[corrupt.length/2]^=1;Files.write(original,corrupt);
            refusal(command,409,null,"corrupt");
            var reduced=(ObjectNode)parse(bytes);reduced.withArray("scans").remove(0);
            Files.writeString(original,canonical(reduced),StandardCharsets.UTF_8);refusal(command,409,null,"reduced");
        } finally {Files.write(original,bytes);}
        assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    }
    private void revoked() throws Exception {
        UUID actor=f.jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user WHERE username=?",(rs,n)->UUID.fromString(rs.getString(1)),reviewer.username());
        int removed=f.jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=(SELECT id FROM role WHERE code='SUPERVISOR')",Ids.bytes(actor));
        assertThat(removed).isEqualTo(1);
        try {refusal(command,403,"PERMISSION_DENIED","revoked");}
        finally {f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(actor));}
    }
    private void refusal(ObjectNode request,int status,String code,String label) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.jdbc,directory.resolve(label));var before=sql.snapshot("before");
        var local=new HandoverTemporaryLocalSql(f.jdbc,directory.resolve(label));var roots=local.snapshot("before");
        reviewer.command(request,status,code);
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(local.snapshot("after")).isEqualTo(roots);
    }
}
