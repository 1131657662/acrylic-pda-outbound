package com.acrylic.outbound.pc;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.pc.PcExceptionChecks.*;

@Component
public class PcExceptionStock {
    private final JdbcTemplate jdbc;private final PcStock stock;private final PcEntries entries;private final JsonText json;
    public PcExceptionStock(JdbcTemplate jdbc,PcStock stock,PcEntries entries,JsonText json) { this.jdbc=jdbc;this.stock=stock;this.entries=entries;this.json=json; }
    public void apply(PcStore.Work work,PcExceptionStore.Plan plan) {
        var rows=validated(work,plan);
        var actions=new HashMap<String,JsonNode>();for(var line:array(plan.body(),"lines"))actions.put(text(line,"entryId"),line);
        for(var entry:rows) {
            var line=actions.get(entry.id());
            if(text(line,"action").equals("RETAIN"))jdbc.update("INSERT INTO pc_retained_entry VALUES(?,?)",Ids.bytes(plan.id()),Ids.bytes(UUID.fromString(entry.id())));
            else dispose(work,plan,new Removal(entry,line));
        }
    }
    public void validate(PcStore.Work work,PcExceptionStore.Plan plan) { validated(work,plan); }
    private List<PcEntries.Entry> validated(PcStore.Work work,PcExceptionStore.Plan plan) {
        var rows=entries.all(work).stream().filter(e->e.status().equals("ACTIVE")).sorted(Comparator.comparing(PcEntries.Entry::baleId)).toList();
        var actions=new HashMap<String,JsonNode>();for(var line:array(plan.body(),"lines"))actions.put(text(line,"entryId"),line);
        check(actions.size()==rows.size(),"PC_DISPOSITION_SET_MISMATCH","原包集合不一致，处置未生效");
        long held=jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE pc_work_id=? AND state='PC_RESERVED'",Long.class,Ids.bytes(work.id()));
        check(held==rows.size(),"PC_COMMITMENT_CONFLICT","本单占用与全部有效包不一致，需先核对");
        for(var entry:rows) {
            var line=actions.get(entry.id());check(line!=null && line.path("expectedCycle").asLong()==entry.cycle(),"PC_DISPOSITION_SET_MISMATCH","原包或周期不匹配");
            held(work,entry);String action=text(line,"action");
            if(action.equals("RETAIN"))retained(work,entry);
        }
        return rows;
    }
    private void held(PcStore.Work work,PcEntries.Entry entry) {
        byte[] bale=Ids.bytes(UUID.fromString(entry.baleId()));
        long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,bale);
        check(cycle==entry.cycle(),"PC_DISPOSITION_CYCLE_CHANGED","包的出库周期已变化，处置未生效，须先对账");
        var held=jdbc.queryForList("SELECT BIN_TO_UUID(pc_work_id) work,state,dispatch_cycle FROM bale_commitment WHERE bale_id=? FOR UPDATE",bale);
        check(held.size()==1 && work.id().toString().equals(held.getFirst().get("work")) && "PC_RESERVED".equals(held.getFirst().get("state"))
            && ((Number)held.getFirst().get("dispatch_cycle")).longValue()==cycle,"PC_COMMITMENT_CONFLICT","原包占用不一致，全部处置保持未生效");
    }
    private void retained(PcStore.Work work,PcEntries.Entry entry) {
        var bale=new PcStock.Bale(UUID.fromString(entry.baleId()),entry.barcode(),entry.deliveryCode(),Long.parseLong(entry.weightGrams()),entry.rawWeight(),entry.model(),entry.name(),entry.cycle());
        check(stock.conflict(work,bale,true)==null,"PC_RETAINED_STOCK_CONFLICT","本车保留包的库存资料存在冲突，请重新核对方案");
    }
    private void dispose(PcStore.Work work,PcExceptionStore.Plan plan,Removal removal) {
        var entry=removal.entry();var line=removal.line();String action=text(line,"action");
        check(Set.of("RELEASE","QUARANTINE").contains(action) && !text(line,"physicalEvidence").isBlank(),"PC_PHYSICAL_CHECK_REQUIRED","减少原包缺少有效实物核验凭据");
        UUID id=Ids.next();byte[] bale=Ids.bytes(UUID.fromString(entry.baleId()));
        jdbc.update("INSERT INTO pc_disposition VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(plan.id()),Ids.bytes(UUID.fromString(entry.id())),action,entry.cycle(),json.digest(entry),text(line,"physicalEvidence"));
        int changed=action.equals("RELEASE")?jdbc.update("DELETE FROM bale_commitment WHERE bale_id=? AND pc_work_id=? AND state='PC_RESERVED'",bale,Ids.bytes(work.id())):
            jdbc.update("UPDATE bale_commitment SET state='QUARANTINED',pc_work_id=NULL,pc_disposition_id=? WHERE bale_id=? AND pc_work_id=? AND state='PC_RESERVED'",Ids.bytes(id),bale,Ids.bytes(work.id()));
        check(changed==1,"PC_COMMITMENT_CONFLICT","原占用已变化，处置未生效");
        BarcodeChanges.bale(jdbc,UUID.fromString(entry.baleId()));
        jdbc.update("UPDATE pc_entry SET disposition_id=? WHERE id=? AND disposition_id IS NULL",Ids.bytes(id),Ids.bytes(UUID.fromString(entry.id())));
    }
    private record Removal(PcEntries.Entry entry,JsonNode line) { }
}
