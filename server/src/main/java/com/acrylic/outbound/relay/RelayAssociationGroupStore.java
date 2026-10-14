package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class RelayAssociationGroupStore {
    private final JdbcTemplate jdbc;
    public RelayAssociationGroupStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void insert(RelayAssociationStore.Row row,JsonNode context,JsonNode request) {
        var o=context.path("original");
        jdbc.update("INSERT INTO independent_association_member VALUES(?,?,?,?,?,?,?,?,?,?)",Ids.bytes(row.id()),
            Ids.bytes(row.scope().warehouse()),Ids.bytes(uuid(o,"relayId")),Ids.bytes(uuid(o,"deviceId")),Ids.bytes(uuid(o,"taskId")),
            Ids.bytes(uuid(request,"physicalCheckId")),request.has("conflictDecisionId")?Ids.bytes(uuid(request,"conflictDecisionId")):null,
            sha(request),sha(context),canonical(context));
    }
    public List<JsonNode> read(RelayAssociationStore.Row row) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(relay_id),BIN_TO_UUID(device_id),BIN_TO_UUID(task_id),BIN_TO_UUID(physical_check_id),
              BIN_TO_UUID(conflict_decision_id),request_sha256,context_sha256,context_text
            FROM independent_association_member WHERE association_id=? AND warehouse_id=? ORDER BY relay_id
            """,(rs,n)->{
                var context=parse(rs.getString(8));var o=context.path("original");
                JsonNode reference=null;
                for(var task:row.request().path("additionalTasks"))if(text(task,"relayId").equals(rs.getString(1)))reference=task;
                if(reference==null || !text(o,"relayId").equals(rs.getString(1)) || !text(o,"warehouseId").equals(row.scope().warehouse().toString())
                    || !text(o,"deviceId").equals(rs.getString(2)) || !text(o,"taskId").equals(rs.getString(3))
                    || !text(reference,"physicalCheckId").equals(rs.getString(4)) || !Objects.equals(reference.has("conflictDecisionId")?text(reference,"conflictDecisionId"):null,rs.getString(5))
                    || !sha(RelayAssociationGroupPolicy.request(row.request(),reference)).equals(rs.getString(6))
                    || !sha(context).equals(rs.getString(7)) || !sha(context).equals(text(reference,"contextSha256")))throw RelayAssociationStore.corrupt();
                return context;
            },Ids.bytes(row.id()),Ids.bytes(row.scope().warehouse()));
    }
}
