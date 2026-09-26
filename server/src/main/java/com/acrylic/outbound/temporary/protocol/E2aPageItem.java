package com.acrylic.outbound.temporary.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record E2aPageItem(String itemKind,UUID originalRecordId,String objectKind,UUID originalObjectId,
                          String itemBytesSha256,int byteCount,long originalDeviceSeq,String recordSha256,
                          String recordStatus,UUID baleId,Long originalCycle,UUID captureId) {
    private static final Set<String> FIELDS=Set.of("itemKind","originalRecordId","objectKind","originalObjectId",
        "itemBytesSha256","byteCount","originalDeviceSeq","recordSha256","recordStatus","baleId",
        "originalCycle","captureId");
    private static final Set<String> ITEM_KINDS=Set.of("CAPTURE","SCAN","AUDIT","STOP","ARCHIVE");
    private static final Set<String> OBJECT_KINDS=Set.of("LOCAL_FROZEN_PAYLOAD","CAPTURE_RECORDS",
        "AUDIT_RECORDS","AUDIT_ARCHIVE");

    public static List<E2aPageItem> parseAll(JsonNode items,long claimedBytes) {
        require(items!=null && items.isArray(),"PAGE_ITEMS_TYPE");
        var result=new ArrayList<E2aPageItem>();
        var records=new HashSet<String>();
        var objects=new HashSet<UUID>();
        long total=0;
        for(var item:items) {
            var parsed=parse(item);
            require(records.add(parsed.itemKind()+"/"+parsed.originalRecordId()),"PAGE_DUPLICATE_RECORD");
            require(objects.add(parsed.originalObjectId()),"PAGE_DUPLICATE_OBJECT");
            total+=parsed.byteCount();
            require(total<=134217728,"PAGE_BYTES_LIMIT");
            result.add(parsed);
        }
        require(total==claimedBytes,"PAGE_BYTES_MISMATCH");
        return List.copyOf(result);
    }

    public static E2aPageItem parse(JsonNode item) {
        require(item!=null && item.isObject(),"PAGE_ITEM_OBJECT");
        var actual=new HashSet<String>();item.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FIELDS),"PAGE_ITEM_FIELDS");
        String kind=text(item,"itemKind",24),objectKind=text(item,"objectKind",32);
        require(ITEM_KINDS.contains(kind) && OBJECT_KINDS.contains(objectKind),"PAGE_ITEM_KIND");
        long bytes=positive(item,"byteCount"),sequence=positive(item,"originalDeviceSeq");
        require(bytes<=33554432,"PAGE_ITEM_BYTES_LIMIT");
        String status=text(item,"recordStatus",40);
        require(status.chars().noneMatch(Character::isISOControl),"PAGE_ITEM_STATUS");
        UUID bale=optionalUuid(item,"baleId"),capture=optionalUuid(item,"captureId");
        Long cycle=item.path("originalCycle").isNull()?null:positive(item,"originalCycle");
        require(!kind.equals("SCAN") || (bale!=null && capture!=null && cycle!=null),"PAGE_SCAN_FIELDS");
        require(kind.equals("SCAN") || (bale==null && capture==null && cycle==null),"PAGE_NONSCAN_FIELDS");
        return new E2aPageItem(kind,uuid(item,"originalRecordId"),objectKind,uuid(item,"originalObjectId"),
            hash(item,"itemBytesSha256"),(int)bytes,sequence,hash(item,"recordSha256"),status,bale,cycle,capture);
    }

    private static UUID optionalUuid(JsonNode item,String field) {
        return item.path(field).isNull()?null:uuid(item,field);
    }
    private static UUID uuid(JsonNode item,String field) {
        String value=text(item,field,36);
        try {
            var parsed=UUID.fromString(value);
            require(parsed.toString().equals(value),"PAGE_ITEM_UUID");
            return parsed;
        } catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("PAGE_ITEM_UUID",invalid); }
    }
    private static String hash(JsonNode item,String field) {
        String value=text(item,field,64);
        require(value.matches("[0-9a-f]{64}"),"PAGE_ITEM_HASH");
        return value;
    }
    private static long positive(JsonNode item,String field) {
        var value=item.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong(),"PAGE_ITEM_NUMBER");
        long number=value.longValue();
        require(number>0 && number<=9007199254740991L,"PAGE_ITEM_NUMBER");
        return number;
    }
    private static String text(JsonNode item,String field,int limit) {
        var value=item.path(field);
        require(value.isTextual() && !value.textValue().isBlank() && value.textValue().length()<=limit,
            "PAGE_ITEM_TEXT");
        return value.textValue();
    }
    private static void require(boolean valid,String code) { if(!valid)throw new IllegalArgumentException(code); }
}
