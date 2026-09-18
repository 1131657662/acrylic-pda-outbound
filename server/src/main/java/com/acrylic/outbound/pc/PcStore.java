package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PcStore {
    private final JdbcTemplate jdbc;
    public PcStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Work find(UUID order) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(w.id),BIN_TO_UUID(w.warehouse_id),BIN_TO_UUID(w.order_id),w.revision,
              BIN_TO_UUID(w.publication_id),BIN_TO_UUID(w.operator_id),u.display_name,w.recovery_epoch,w.status,
              w.row_version,BIN_TO_UUID(w.writer_id),w.writer_fence,w.next_sequence,w.work_round,
              BIN_TO_UUID(r.inventory_snapshot_id),p.auto_deduct_inventory
            FROM pc_work w INNER JOIN app_user u ON u.id=w.operator_id
            INNER JOIN order_publication p ON p.id=w.publication_id
            INNER JOIN order_revision r ON r.order_id=w.order_id AND r.revision=w.revision WHERE w.order_id=?
            """,(rs,n)->new Work(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),order,rs.getInt(4),
                UUID.fromString(rs.getString(5)),UUID.fromString(rs.getString(6)),rs.getString(7),rs.getString(8),rs.getString(9),
                rs.getString(10),rs.getString(11),rs.getString(12),rs.getLong(13),rs.getLong(14),UUID.fromString(rs.getString(15)),rs.getBoolean(16)),Ids.bytes(order));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Work required(UUID order) {
        var work=find(order);
        if(work==null) throw new BusinessException(404,"PC_WORK_NOT_FOUND","本单尚未开始PC作业");return work;
    }
    public void writer(Work work,UUID actor,PcCommands.Write command) {
        owner(work,actor);version(work,command.expectedVersion());
        if(!work.status().equals("ACTIVE")) throw new BusinessException(409,"PC_WORK_FROZEN","本单已结单，不能继续录入或撤销");
        if(!work.writerId().equals(command.writerId().toString()) || !work.fence().equals(command.fence()))
            throw new BusinessException(409,"PC_WRITER_REPLACED","另一窗口已取得写入资格，本窗口已停止写入，请核对后在当前窗口继续");
    }
    public void owner(Work work,UUID actor) {
        if(!work.operator().equals(actor)) throw new BusinessException(403,"PC_OTHER_OPERATOR","本单由其他人员持有，请由原作业人员继续");
    }
    public void version(Work work,String expected) {
        if(!work.version().equals(expected)) throw new BusinessException(409,"PC_VERSION_CONFLICT","作业记录已变化，本次未执行，请刷新后重新核对");
    }
    public void advance(Work work,String status) {
        jdbc.update("UPDATE pc_work SET status=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,Ids.bytes(work.id()));
    }
    public record Work(UUID id,UUID warehouse,UUID order,int revision,UUID publication,UUID operator,String operatorName,
        String epoch,String status,String version,String writerId,String fence,long nextSequence,long round,UUID snapshot,boolean deduct) { }
}
