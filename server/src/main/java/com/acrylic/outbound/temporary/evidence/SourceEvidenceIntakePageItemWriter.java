package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SourceEvidenceIntakePageItemWriter {
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeRawObjects rawObjects;
    private final SourceEvidenceIntakeObjectFiles files;
    private final SourceEvidenceIntakePageItems staged;

    public SourceEvidenceIntakePageItemWriter(SourceEvidenceIntakeTransactions transactions,
        SourceEvidenceIntakeRawObjects rawObjects,SourceEvidenceIntakeObjectFiles files,
        SourceEvidenceIntakePageItems staged) {
        this.transactions=transactions;this.rawObjects=rawObjects;this.files=files;this.staged=staged;
    }

    public Prepared preflight(SourceEvidenceIntakePageObjects.Binding binding,SourceEvidenceIntakePage.Parsed page) {
        var raw=transactions.read(status->fetch(binding,page.items()));
        for(var object:raw) {
            try { files.verify(object.stored()); }
            catch(IOException failure) { throw unavailable(); }
        }
        return new Prepared(raw);
    }

    public void stageNew(SourceEvidenceIntakePageObjects.Binding binding,SourceEvidenceIntakePage.Parsed page,
        SourceEvidenceIntakePageObjects.PageObject object,Prepared prepared) {
        var context=new SourceEvidenceIntakePageItems.PageContext(binding,page.pageNo(),object.id());
        var indexed=recheck(binding,page.items(),prepared);
        require(staged.existing(context).isEmpty(),"E2A_PAGE_ITEM_INTEGRITY_FAILURE");
        var totals=staged.totals(binding);
        long bytes=page.items().stream().mapToLong(E2aPageItem::byteCount).sum();
        require(totals.bytes()<=134217728-bytes,"E2A_PAGE_BYTES_LIMIT");
        for(var entry:indexed)staged.insert(context,entry);
    }

    public void verifyExisting(SourceEvidenceIntakePageObjects.Binding binding,SourceEvidenceIntakePage.Parsed page,
        SourceEvidenceIntakePageObjects.PageObject object,Prepared prepared) {
        var context=new SourceEvidenceIntakePageItems.PageContext(binding,page.pageNo(),object.id());
        var indexed=recheck(binding,page.items(),prepared);
        require(staged.existing(context).equals(indexed),"E2A_PAGE_ITEM_INTEGRITY_FAILURE");
    }

    private List<SourceEvidenceIntakeRawObjects.RawObject> fetch(SourceEvidenceIntakePageObjects.Binding binding,
        List<E2aPageItem> items) {
        var result=new ArrayList<SourceEvidenceIntakeRawObjects.RawObject>();
        for(var item:items) {
            var object=rawObjects.findAfterSourceLock(rawBinding(binding,item));
            require(object!=null,"E2A_RAW_OBJECT_MISSING");
            require(matches(item,object),"E2A_RAW_OBJECT_CONFLICT");
            result.add(object);
        }
        return List.copyOf(result);
    }

    private List<SourceEvidenceIntakePageItems.Indexed> recheck(SourceEvidenceIntakePageObjects.Binding binding,
        List<E2aPageItem> items,Prepared prepared) {
        require(items.size()==prepared.raw().size(),"E2A_RAW_OBJECT_CHANGED");
        var result=new ArrayList<SourceEvidenceIntakePageItems.Indexed>();
        for(int ordinal=0;ordinal<items.size();ordinal++) {
            var item=items.get(ordinal);
            var object=rawObjects.findAfterSourceLock(rawBinding(binding,item));
            require(object!=null && object.equals(prepared.raw().get(ordinal)) && matches(item,object),
                "E2A_RAW_OBJECT_CHANGED");
            result.add(SourceEvidenceIntakePageItems.index(ordinal,item,object));
        }
        return List.copyOf(result);
    }

    private SourceEvidenceIntakeRawObjects.Binding rawBinding(SourceEvidenceIntakePageObjects.Binding binding,
        E2aPageItem item) {
        return new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(),binding.scopeId(),
            binding.submissionId(),binding.workRound(),binding.creatorDeviceId(),binding.originalOperatorId(),
            item.objectKind(),item.originalObjectId());
    }

    private boolean matches(E2aPageItem item,SourceEvidenceIntakeRawObjects.RawObject object) {
        return item.originalObjectId().equals(object.originalObjectId())
            && item.itemBytesSha256().equals(object.stored().sha256())
            && item.byteCount()==object.stored().bytes();
    }

    private static void require(boolean condition,String code) {
        if(!condition)throw new BusinessException(409,code,"页条目与原始对象不一致");
    }
    private static BusinessException unavailable() {
        return new BusinessException(409,"E2A_RAW_OBJECT_FILE_UNAVAILABLE","原始对象文件缺失或损坏");
    }

    public record Prepared(List<SourceEvidenceIntakeRawObjects.RawObject> raw) {
        public Prepared { raw=List.copyOf(raw); }
    }
}
