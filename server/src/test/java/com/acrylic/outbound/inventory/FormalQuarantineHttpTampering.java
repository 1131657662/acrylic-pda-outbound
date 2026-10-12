package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class FormalQuarantineHttpTampering {
    private FormalQuarantineHttpTampering() { }
    static void verify(FormalQuarantineHttpSource source,CorrectionAttachmentHttp actor,String path,JsonNode origin) throws Exception {
        var jdbc=source.jdbc;String id=origin.path("dispositionId").asText();
        change(jdbc,()->jdbc.update("UPDATE physical_disposition_evidence SET physical_evidence='changed original evidence' WHERE id=UUID_TO_BIN(?)",id),actor,path);
        change(jdbc,()->jdbc.update("UPDATE physical_disposition_evidence SET record_sha256=UNHEX(REPEAT('0',64)) WHERE id=UUID_TO_BIN(?)",id),actor,path);
        change(jdbc,()->jdbc.update("UPDATE physical_disposition_evidence SET applied_submission_id=NULL WHERE id=UUID_TO_BIN(?)",id),actor,path,404);
        change(jdbc,()->jdbc.update("UPDATE order_continuation SET activation_text=JSON_SET(activation_text,'$.status','UNKNOWN') WHERE id=UUID_TO_BIN(?)",origin.path("continuationId").asText()),actor,path);
        change(jdbc,()->jdbc.update("UPDATE submission SET receipt_text=JSON_SET(receipt_text,'$.status','UNKNOWN') WHERE id=UUID_TO_BIN(?)",origin.path("submissionId").asText()),actor,path);
    }
    private static void change(JdbcTemplate jdbc,Runnable change,CorrectionAttachmentHttp actor,String path) throws Exception { change(jdbc,change,actor,path,409); }
    private static void change(JdbcTemplate jdbc,Runnable change,CorrectionAttachmentHttp actor,String path,int status) throws Exception {
        var transaction=new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()).getTransaction(new org.springframework.transaction.support.DefaultTransactionDefinition());
        // Commit the controlled corruption for another real HTTP connection, preserving original values in a separate projection.
        var original=jdbc.queryForList("SELECT id,physical_evidence,record_sha256,applied_submission_id FROM physical_disposition_evidence");
        var continuations=jdbc.queryForList("SELECT id,activation_text FROM order_continuation");var submissions=jdbc.queryForList("SELECT id,receipt_text FROM submission");
        var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource());change.run();manager.commit(transaction);
        try { var refused=SubmissionJson.parse(actor.get(path,status));assertThat(refused.path("code").asText()).isNotEmpty(); }
        finally {
            for(var row:original)jdbc.update("UPDATE physical_disposition_evidence SET physical_evidence=?,record_sha256=?,applied_submission_id=? WHERE id=?",row.get("physical_evidence"),row.get("record_sha256"),row.get("applied_submission_id"),row.get("id"));
            for(var row:continuations)jdbc.update("UPDATE order_continuation SET activation_text=? WHERE id=?",row.get("activation_text"),row.get("id"));
            for(var row:submissions)jdbc.update("UPDATE submission SET receipt_text=? WHERE id=?",row.get("receipt_text"),row.get("id"));
        }
    }
}
