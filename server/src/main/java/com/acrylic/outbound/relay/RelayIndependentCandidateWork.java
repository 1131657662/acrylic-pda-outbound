package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Hash actual work states as well as their existence, so an old page cannot hide later transitions. */
@Repository
public class RelayIndependentCandidateWork {
    private final NamedParameterJdbcTemplate jdbc;
    public RelayIndependentCandidateWork(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void attach(List<ObjectNode> heads) {
        int[] total={0};
        for(int offset=0;offset<heads.size();offset+=500) {
            var chunk=heads.subList(offset,Math.min(offset+500,heads.size()));var byId=new HashMap<String,ObjectNode>();
            chunk.forEach(head->{head.putArray("workFacts");byId.put(text(head,"id"),head);});
            var params=new MapSqlParameterSource("ids",chunk.stream().map(head->Ids.bytes(uuid(head,"id"))).toList());
            jdbc.query(sql()+" ORDER BY owner,kind,identity LIMIT 20001",params,(org.springframework.jdbc.core.RowCallbackHandler)rs->{
                RelayIndependentCandidateQuery.capacity(++total[0],20000);
                byId.get(rs.getString(1)).withArray("workFacts").add(parse(rs.getString(4)));
            });
        }
    }
    private String sql() {
        return """
            SELECT BIN_TO_UUID(order_id) owner,'ASSIGNMENT' kind,BIN_TO_UUID(id) identity,
              JSON_OBJECT('kind','ASSIGNMENT','id',BIN_TO_UUID(id),'status',status,'epoch',CAST(epoch AS CHAR),
                'revision',CAST(revision AS CHAR),'deviceId',BIN_TO_UUID(device_id),'operatorId',BIN_TO_UUID(operator_id),
                'issuedAt',CAST(issued_at AS CHAR),'expiresAt',CAST(expires_at AS CHAR),'downloadAckAt',CAST(download_ack_at AS CHAR)) facts
              FROM order_assignment WHERE order_id IN (:ids)
            UNION ALL
            SELECT BIN_TO_UUID(order_id),'PC',BIN_TO_UUID(id),JSON_OBJECT('kind','PC','id',BIN_TO_UUID(id),'status',status,
              'rowVersion',CAST(row_version AS CHAR),'workRound',CAST(work_round AS CHAR),'nextSequence',CAST(next_sequence AS CHAR))
              FROM pc_work WHERE order_id IN (:ids)
            UNION ALL
            SELECT BIN_TO_UUID(o.id),'SUBMISSION',BIN_TO_UUID(s.id),JSON_OBJECT('kind','SUBMISSION','id',BIN_TO_UUID(s.id),
              'status',s.status,'workRound',CAST(s.work_round AS CHAR),'receiptSha256',SHA2(s.receipt_text,256),
              'issuesSha256',SHA2(CAST(s.issues_json AS CHAR),256),'updatedAt',CAST(s.updated_at AS CHAR))
              FROM outbound_order o INNER JOIN submission_scope c ON c.source_order_id=o.id OR c.canonical_order_id=o.id
                INNER JOIN submission s ON s.scope_id=c.id WHERE o.id IN (:ids)
            UNION ALL
            SELECT BIN_TO_UUID(order_id),'RESULT',CAST(result_version AS CHAR),JSON_OBJECT('kind','RESULT',
              'version',CAST(result_version AS CHAR),'submissionId',BIN_TO_UUID(submission_id),'actualCount',CAST(actual_count AS CHAR),
              'weightGrams',CAST(weight_g AS CHAR),'inventoryDeducted',CAST(inventory_deducted AS CHAR),
              'headerSha256',SHA2(CAST(header_snapshot AS CHAR),256)) FROM order_result_version WHERE order_id IN (:ids)
            """;
    }
}
