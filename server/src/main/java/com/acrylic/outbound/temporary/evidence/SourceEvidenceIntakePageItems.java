package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakePageItems {
    private static final String FIND="""
        SELECT warehouse_id,principal_id,open_original_key,page_no,
          item_ordinal,item_kind,original_record_id,original_object_id,raw_object_id,byte_count
        FROM source_evidence_page_item_stage
        WHERE page_object_id=? AND scope_id=? AND submission_id=? AND work_round=?
        ORDER BY item_ordinal
        """;
    private static final String TOTAL="""
        SELECT COALESCE(SUM(byte_count),0) AS bytes,COUNT(*) AS entries
        FROM source_evidence_page_item_stage
        WHERE scope_id=? AND submission_id=? AND work_round=? AND open_original_key=?
        """;
    private static final String INSERT="""
        INSERT INTO source_evidence_page_item_stage
        (warehouse_id,scope_id,submission_id,work_round,principal_id,route_scope,open_original_key,
         page_object_id,page_no,item_ordinal,item_kind,original_record_id,original_object_id,raw_object_id,byte_count)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakePageItems(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(propagation=Propagation.MANDATORY)
    public List<Indexed> existing(PageContext context) {
        var binding=context.binding();
        var rows=jdbc.queryForList(FIND,Ids.bytes(context.pageObjectId()),Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()),binding.workRound());
        var result=new ArrayList<Indexed>();
        for(Map<String,Object> row:rows) {
            if(!(row.get("warehouse_id") instanceof byte[] warehouse)
                || !(row.get("principal_id") instanceof byte[] principal)
                || !(row.get("open_original_key") instanceof byte[] open)
                || !java.util.Arrays.equals(warehouse,Ids.bytes(binding.warehouseId()))
                || !java.util.Arrays.equals(principal,Ids.bytes(binding.originalOperatorId()))
                || !java.util.Arrays.equals(open,Ids.bytes(binding.openKey()))
                || !(row.get("page_no") instanceof Number pageNo) || pageNo.longValue()!=context.pageNo()
                || !(row.get("item_ordinal") instanceof Number ordinal) || ordinal.intValue()!=result.size()
                || !(row.get("item_kind") instanceof String kind)
                || !(row.get("original_record_id") instanceof byte[] record)
                || !(row.get("original_object_id") instanceof byte[] original)
                || !(row.get("raw_object_id") instanceof byte[] raw)
                || !(row.get("byte_count") instanceof Number count) || count.longValue()<1 || count.longValue()>33554432)
                throw corrupt();
            result.add(new Indexed(result.size(),kind,uuid(record),uuid(original),uuid(raw),count.intValue()));
        }
        return List.copyOf(result);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Totals totals(SourceEvidenceIntakePageObjects.Binding binding) {
        var rows=jdbc.queryForList(TOTAL,Ids.bytes(binding.scopeId()),Ids.bytes(binding.submissionId()),
            binding.workRound(),Ids.bytes(binding.openKey()));
        if(rows.size()!=1 || !(rows.getFirst().get("bytes") instanceof Number bytes)
            || !(rows.getFirst().get("entries") instanceof Number entries)
            || bytes.longValue()<0 || entries.longValue()<0)throw corrupt();
        return new Totals(bytes.longValue(),entries.longValue());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void insert(PageContext context,Indexed item) {
        var binding=context.binding();
        try {
            if(jdbc.update(INSERT,Ids.bytes(binding.warehouseId()),Ids.bytes(binding.scopeId()),
                Ids.bytes(binding.submissionId()),binding.workRound(),Ids.bytes(binding.originalOperatorId()),
                E2aProtocol.ROUTE_SCOPE,Ids.bytes(binding.openKey()),Ids.bytes(context.pageObjectId()),
                context.pageNo(),item.ordinal(),item.kind(),Ids.bytes(item.recordId()),Ids.bytes(item.originalObjectId()),
                Ids.bytes(item.rawObjectId()),item.byteCount())!=1)throw new IllegalStateException("PAGE_ITEM_INSERT_COUNT");
        } catch(DuplicateKeyException duplicate) {
            throw new BusinessException(409,"E2A_PAGE_ITEM_DUPLICATE","同来源存在重复原记录或原对象");
        }
    }

    public static Indexed index(int ordinal,E2aPageItem item,SourceEvidenceIntakeRawObjects.RawObject raw) {
        if(ordinal<0 || item==null || raw==null || !item.originalObjectId().equals(raw.originalObjectId()))
            throw new IllegalArgumentException("PAGE_ITEM_INDEX");
        return new Indexed(ordinal,item.itemKind(),item.originalRecordId(),item.originalObjectId(),
            raw.id(),item.byteCount());
    }

    private static UUID uuid(byte[] bytes) {
        if(bytes.length!=16)throw corrupt();
        var buffer=java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(),buffer.getLong());
    }
    private static BusinessException corrupt() {
        return new BusinessException(409,"E2A_PAGE_ITEM_INTEGRITY_FAILURE","已存页条目索引损坏");
    }

    public record PageContext(SourceEvidenceIntakePageObjects.Binding binding,long pageNo,UUID pageObjectId) {
        public PageContext {
            if(binding==null || pageNo<1 || pageNo>4096 || pageObjectId==null)
                throw new IllegalArgumentException("PAGE_ITEM_CONTEXT");
        }
    }
    public record Indexed(int ordinal,String kind,UUID recordId,UUID originalObjectId,UUID rawObjectId,int byteCount) { }
    public record Totals(long bytes,long entries) { }
}
