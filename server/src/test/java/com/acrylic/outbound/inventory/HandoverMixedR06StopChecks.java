package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Attributes the third round's STOP tail fault before any successful stop. */
final class HandoverMixedR06StopChecks {
    static void verify(HandoverTemporaryTargetFixture f,Path directory) throws Exception {
        var request=(ObjectNode)parse(Files.readAllBytes(directory.resolve("android-continuation-stop-request.json")));
        UUID id=uuid(request,"submissionId");String path="/temporary-orders/continuation-submissions/"+id+"/stop";
        var evidence=directory.resolve("reverse-stop-sql");var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,evidence);
        var continuation=new HandoverMixedContinuationSql(f.source.jdbc,evidence);
        var before=sql.snapshot("before");var original=continuation.snapshot("before");
        f.source.post(path,request,f.source.otherDeviceToken,404);
        f.source.post(path,request.deepCopy().put("verificationId",UUID.randomUUID().toString()),f.token,409);
        f.source.jdbc.execute("CREATE TRIGGER reverse_r06_stop_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='SUBMISSION_ABORTED' AND NEW.entity_id=UUID_TO_BIN('"+id+"') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_REVERSE_R06_STOP_TAIL'; END IF; END");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        try {f.source.post(path,request,f.token,500);HandoverTemporaryIntakeChecks.requireFault(logs.list,"D06_REVERSE_R06_STOP_TAIL");}
        finally {f.source.jdbc.execute("DROP TRIGGER reverse_r06_stop_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(continuation.snapshot("after")).isEqualTo(original);
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,Ids.bytes(id))).isEqualTo("REVIEW_REQUIRED");
        Files.writeString(evidence.resolve("exact-sql-fault.txt"),"D06_REVERSE_R06_STOP_TAIL SQLSTATE=45000 code=1644; wrong identity/proof and tail rollback preserve all rows\n");
    }
}
