package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

record TemporaryDuplicateCursor(String context,String candidates,Instant createdAt,UUID order) {
    static TemporaryDuplicateCursor parse(String input,String context,String candidates) {
        if(input.isEmpty()) return null;
        TemporaryDuplicateCursor cursor;
        try {
            if(input.length()>400) throw new IllegalArgumentException();
            var parts=new String(Base64.getUrlDecoder().decode(input),StandardCharsets.US_ASCII).split("\\|",-1);
            if(parts.length!=4 || !parts[0].matches("[a-f0-9]{64}") || !parts[1].matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            cursor=new TemporaryDuplicateCursor(parts[0],parts[1],Instant.parse(parts[2]),UUID.fromString(parts[3]));
            if(!cursor.order().toString().equals(parts[3]) || cursor.createdAt().getNano()%1_000_000!=0
                || cursor.createdAt().isBefore(Instant.parse("1000-01-01T00:00:00Z"))
                || cursor.createdAt().isAfter(Instant.parse("9999-12-31T23:59:59.999Z"))) throw new IllegalArgumentException();
        } catch(IllegalArgumentException | DateTimeException error) {
            throw new BusinessException(400,"INVALID_CURSOR","重复候选分页位置无效，请重新查询");
        }
        if(!cursor.context().equals(context) || !cursor.candidates().equals(candidates))
            throw new BusinessException(409,"TEMPORARY_DUPLICATE_CONTEXT_CHANGED","原批次、查询范围或候选已变化，请重新查询全部候选");
        return cursor;
    }
    static String encode(String context,String candidates,TemporaryDuplicateModels.Head last) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (context+"|"+candidates+"|"+last.createdAt()+"|"+last.id()).getBytes(StandardCharsets.US_ASCII));
    }
}
