package com.acrylic.outbound.inventory;

import java.util.List;

final class PcQuarantineHttpTampering {
    private PcQuarantineHttpTampering() { }
    static void verify(PcQuarantineHttpSource f,CorrectionAttachmentHttp reader,String path) throws Exception {
        for(String table:List.of("pc_exception_case","pc_exception_plan")) {
            String column=table.equals("pc_exception_case")?"snapshot_sha256":"payload_sha256";
            String old=f.jdbc.queryForObject("SELECT "+column+" FROM "+table,String.class);
            f.jdbc.update("UPDATE "+table+" SET "+column+"=?","0".repeat(64)); reader.get(path,409);
            f.jdbc.update("UPDATE "+table+" SET "+column+"=?",old);
        }
        f.jdbc.update("UPDATE pc_entry SET weight_g=weight_g+1 WHERE disposition_id IN (SELECT id FROM pc_disposition WHERE action='QUARANTINE')");
        reader.get(path,409);
        f.jdbc.update("UPDATE pc_entry SET weight_g=weight_g-1 WHERE disposition_id IN (SELECT id FROM pc_disposition WHERE action='QUARANTINE')");
        f.jdbc.update("UPDATE pc_exception_review SET decision='REJECT'"); reader.get(path,409);
        f.jdbc.update("UPDATE pc_exception_review SET decision='APPROVE'");
        planReceipt(f,reader,path);
    }
    private static void planReceipt(PcQuarantineHttpSource f,CorrectionAttachmentHttp reader,String path) throws Exception {
        var row=f.jdbc.queryForMap("SELECT payload_text,payload_sha256 FROM pc_exception_plan");
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)com.acrylic.outbound.submissions.SubmissionJson.parse(row.get("payload_text").toString());
        changed.put("reason","ISOLATED altered original plan with a self-consistent digest");
        f.jdbc.update("UPDATE pc_exception_plan SET payload_text=?,payload_sha256=?",
            com.acrylic.outbound.submissions.SubmissionJson.canonical(changed),com.acrylic.outbound.submissions.SubmissionJson.sha(changed));
        reader.get(path,409);
        f.jdbc.update("UPDATE pc_exception_plan SET payload_text=?,payload_sha256=?",row.get("payload_text"),row.get("payload_sha256"));
    }
}
