package com.acrylic.outbound.submissions;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionEvents {
    private final JdbcTemplate jdbc;private final AuditWriter audit;
    private volatile Boolean captureOperationRevision;
    public SubmissionEvents(JdbcTemplate jdbc,AuditWriter audit) { this.jdbc=jdbc;this.audit=audit; }
    public void aborted(SubmissionStore.Head head,JsonNode receipt) {
        audit.append(new AuditWriter.Event(head.actor(),"SUBMISSION_ABORTED","SUBMISSION",head.control().id(),canonical(receipt)),head.control().id().toString());
    }
    public void transition(SubmissionStore.Head head,String status,UUID actor) {
        if(status.equals("REVIEW_REQUIRED") && jdbc.queryForObject("SELECT COUNT(*) FROM order_change_request WHERE order_id=? AND status NOT IN ('CANCELLED','WITHDRAWN') AND NOT EXISTS (SELECT 1 FROM order_continuation t WHERE t.request_id=order_change_request.id AND t.status!='REQUESTED')",Integer.class,Ids.bytes(head.order()))>0)
            status="RECALL_PENDING";
        var c=head.control();jdbc.update("UPDATE outbound_order SET status=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,Ids.bytes(head.order()));
        var detail=object().put("submissionId",c.id().toString()).put("status",status);
        long version=jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",Long.class,Ids.bytes(head.order()));
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,?,?,?,?,?,?,NULL,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Long.parseLong(c.epoch()),
            Ids.bytes(head.warehouse()),Ids.bytes(head.order()),version,"SUBMISSION_"+status,canonical(detail));
        audit.append(new AuditWriter.Event(actor,"SUBMISSION_"+status,"ORDER",head.order(),canonical(detail)),c.id().toString());
    }
    public void evidence(SubmissionStore.Head head,JsonNode body) {
        var c=head.control();
        var captures=new ArrayList<JsonNode>();var existingCaptures=captureFacts(array(body,"captures"));
        array(body,"captures").forEach(row->{
            String digest=sha(row),byOrigin=existingCaptures.byOrigin().get(captureKey(row));
            if(byOrigin!=null&&!byOrigin.equals(digest))throw new com.acrylic.outbound.common.BusinessException(409,"CAPTURE_CONTENT_CONFLICT","原设备采集序号已用于不同事实，请保留原记录核对");
            String byId=existingCaptures.byId().get(uuid(row,"id").toString());
            if(byId==null)captures.add(row);else equal(byId,digest,"原UUID对应事实被改写");
        });
        insertCaptures(c,head,captures);
        var auditEvents=new ArrayList<JsonNode>();array(body,"events").forEach(auditEvents::add);
        com.acrylic.outbound.audit.PdaAuditFacts.verifyAll(jdbc,auditEvents);var existingEvents=existingIds("submitted_event",auditEvents);
        auditEvents.removeIf(row->existingEvents.containsKey(uuid(row,"id").toString()));
        insertEvents(c,auditEvents);
    }
    private void insertCaptures(SubmissionControl c,SubmissionStore.Head head,List<JsonNode> rows) {
        boolean withRevision=captureOperationRevision();
        String columns="id,submission_id,order_id,origin_device_id,device_capture_seq,order_scan_seq,record_sha256"+(withRevision?",operation_revision":"");
        for(int start=0;start<rows.size();start+=200) {
            int end=Math.min(start+200,rows.size());var sql=new StringBuilder("INSERT INTO submitted_capture(").append(columns).append(") VALUES ");var params=new ArrayList<Object>();
            for(int i=start;i<end;i++) { if(i>start)sql.append(',');sql.append(withRevision?"(?,?,?,?,?,?,?,?)":"(?,?,?,?,?,?,?)");var row=rows.get(i);params.add(Ids.bytes(uuid(row,"id")));params.add(Ids.bytes(c.id()));params.add(Ids.bytes(head.order()));params.add(Ids.bytes(uuid(row,"originDeviceId")));params.add(number(row,"deviceCaptureSeq"));params.add(number(row,"orderScanSeq"));params.add(sha(row));if(withRevision)params.add(row.path("orderRevision").asInt(1)); }
            jdbc.update(sql.toString(),params.toArray());
        }
    }
    private boolean captureOperationRevision() {
        var cached=captureOperationRevision;
        if(cached!=null)return cached;
        synchronized(this) {
            if(captureOperationRevision==null)captureOperationRevision=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='submitted_capture' AND column_name='operation_revision'",Integer.class)>0;
            return captureOperationRevision;
        }
    }
    private void insertEvents(SubmissionControl c,List<JsonNode> rows) {
        for(int start=0;start<rows.size();start+=200) {
            int end=Math.min(start+200,rows.size());var sql=new StringBuilder("INSERT INTO submitted_event VALUES ");var params=new ArrayList<Object>();
            for(int i=start;i<end;i++) { if(i>start)sql.append(',');sql.append("(?,?,?,?,?)");var row=rows.get(i);params.add(Ids.bytes(uuid(row,"id")));params.add(Ids.bytes(c.id()));params.add(Ids.bytes(uuid(row,"originDeviceId")));params.add(number(row,"localSeq"));params.add(sha(row)); }
            jdbc.update(sql.toString(),params.toArray());
        }
    }
    private CaptureFacts captureFacts(Iterable<JsonNode> input) {
        var rows=new ArrayList<JsonNode>();input.forEach(rows::add);var byId=new HashMap<String,String>();var byOrigin=new HashMap<String,String>();
        readCaptureIds(rows,byId);var origins=new LinkedHashMap<String,List<JsonNode>>();for(var row:rows)origins.computeIfAbsent(uuid(row,"originDeviceId").toString(),key->new ArrayList<>()).add(row);
        for(var group:origins.entrySet())readCaptureOrigins(group.getKey(),group.getValue(),byOrigin);
        return new CaptureFacts(byId,byOrigin);
    }
    private void readCaptureIds(List<JsonNode> rows,Map<String,String> result) {
        for(int start=0;start<rows.size();start+=200) { int end=Math.min(start+200,rows.size());var sql="SELECT BIN_TO_UUID(id),record_sha256 FROM submitted_capture WHERE id IN ("+marks(end-start)+")";var params=new ArrayList<Object>();for(int i=start;i<end;i++)params.add(Ids.bytes(uuid(rows.get(i),"id")));jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->{result.put(rs.getString(1),rs.getString(2));},params.toArray()); }
    }
    private void readCaptureOrigins(String origin,List<JsonNode> rows,Map<String,String> result) {
        for(int start=0;start<rows.size();start+=200) { int end=Math.min(start+200,rows.size());var sql="SELECT device_capture_seq,record_sha256 FROM submitted_capture WHERE origin_device_id=? AND device_capture_seq IN ("+marks(end-start)+")";var params=new ArrayList<Object>();params.add(Ids.bytes(UUID.fromString(origin)));for(int i=start;i<end;i++)params.add(number(rows.get(i),"deviceCaptureSeq"));jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->{result.put(captureKey(origin,rs.getLong(1)),rs.getString(2));},params.toArray()); }
    }
    private Map<String,String> existingIds(String table,Iterable<JsonNode> input) {
        var rows=new ArrayList<JsonNode>();input.forEach(rows::add);var result=new HashMap<String,String>();
        for(int start=0;start<rows.size();start+=200) {
            int end=Math.min(start+200,rows.size());var sql=new StringBuilder("SELECT BIN_TO_UUID(id),record_sha256 FROM ").append(table).append(" WHERE id IN (");var params=new ArrayList<Object>();
            for(int i=start;i<end;i++) { if(i>start)sql.append(',');sql.append('?');params.add(Ids.bytes(uuid(rows.get(i),"id"))); }sql.append(')');
            jdbc.query(sql.toString(),(org.springframework.jdbc.core.RowCallbackHandler)rs->{ result.put(rs.getString(1),rs.getString(2)); },params.toArray());
        }
        return result;
    }
    private static String captureKey(JsonNode row) { return captureKey(uuid(row,"originDeviceId").toString(),number(row,"deviceCaptureSeq")); }
    private static String captureKey(String origin,long sequence) { return origin+":"+sequence; }
    private static String marks(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    private record CaptureFacts(Map<String,String> byId,Map<String,String> byOrigin) { }
}
