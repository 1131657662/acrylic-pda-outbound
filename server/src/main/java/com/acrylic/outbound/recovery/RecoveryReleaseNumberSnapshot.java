package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original global counter boundaries, pinned outside the control and restore sets. */
final class RecoveryReleaseNumberSnapshot {
    static JsonNode read(RecoveryClosedAuthority authority,RecoveryReleaseNumberHistory.Snapshot snapshot,String schema) {
        require(snapshot!=null && snapshot.independentSha256()!=null && snapshot.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(snapshot.independentSha256()),"RECOVERY_NUMBER_SNAPSHOT_PIN");
        try {
            var path=RecoveryControlPaths.file(snapshot.file()); var location=authority.location().checked();
            require(!RecoveryControlPaths.overlap(path,location.root()) && !RecoveryControlPaths.overlap(path,authority.authorityRoot())
                && !RecoveryControlPaths.overlap(path,authority.checkpoint()),"RECOVERY_NUMBER_SNAPSHOT_INSIDE_AUTHORITY");
            for(var restored:location.restoreRoots())
                require(!RecoveryControlPaths.overlap(path,restored),"RECOVERY_NUMBER_SNAPSHOT_INSIDE_RESTORE");
            byte[] bytes;
            try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(33554433); }
            require(bytes.length>0 && bytes.length<=33554432 && sha256(bytes).equals(snapshot.independentSha256()),
                "RECOVERY_NUMBER_SNAPSHOT_PIN_MISMATCH");
            var value=SubmissionJson.parse(bytes); fields(value,"tables files schema flywayHistory database");
            require(text(value,"database").equals(schema),"RECOVERY_NUMBER_SNAPSHOT_DATABASE");
            return value;
        } catch(java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_NUMBER_SNAPSHOT_UNAVAILABLE",unavailable); }
    }

    static SortedMap<LocalDate,Integer> counters(JsonNode table) {
        require(table.path("columns").equals(SubmissionJson.object().putArray("columns").add("business_date").add("last_sequence"))
            && table.path("rows").isArray() && table.path("rows").size()<=65536,"RECOVERY_NUMBER_SNAPSHOT_COLUMNS");
        var result=new TreeMap<LocalDate,Integer>();
        for(var row:table.path("rows")) {
            require(row.isArray() && row.size()==2,"RECOVERY_NUMBER_SNAPSHOT_ROW");
            LocalDate date=date(value(row,0)); String sequence=value(row,1);
            require(sequence.matches("0|[1-9][0-9]{0,9}"),"RECOVERY_NUMBER_SNAPSHOT_SEQUENCE");
            long number=Long.parseLong(sequence);
            require(number<=Integer.MAX_VALUE && result.put(date,(int)number)==null,"RECOVERY_NUMBER_SNAPSHOT_SEQUENCE");
        }
        return Collections.unmodifiableSortedMap(result);
    }

    static void orders(JsonNode snapshot,SortedMap<LocalDate,Integer> counters) {
        var table=snapshot.path("tables").path("outbound_order"); var columns=table.path("columns");
        require(columns.isArray() && table.path("rows").isArray() && table.path("rows").size()<=65536,"RECOVERY_NUMBER_ORDERS");
        var names=new ArrayList<String>(); columns.forEach(column->names.add(column.asText()));
        require(new HashSet<>(names).size()==names.size() && names.containsAll(List.of("id","order_no")),"RECOVERY_NUMBER_ORDERS");
        var numbers=new HashSet<String>(); var ids=new HashSet<String>();
        for(var row:table.path("rows")) {
            require(row.isArray() && row.size()==names.size() && ids.add(value(row,names.indexOf("id"))),"RECOVERY_NUMBER_ORDERS");
            String number=value(row,names.indexOf("order_no"));
            require(numbers.add(number),"RECOVERY_NUMBER_DUPLICATE");
            if(number.startsWith("FH")) order(number,counters);
        }
    }

    private static void order(String number,SortedMap<LocalDate,Integer> counters) {
        require(number.matches("FH[0-9]{8}[0-9]{4,10}"),"RECOVERY_NUMBER_ORDER_FORMAT");
        LocalDate date=date(number.substring(2,6)+"-"+number.substring(6,8)+"-"+number.substring(8,10));
        long sequence=Long.parseLong(number.substring(10));
        require(sequence>0 && sequence<=Integer.MAX_VALUE
            && number.substring(10).equals(String.format(Locale.ROOT,"%04d",sequence))
            && counters.containsKey(date) && sequence<=counters.get(date),"RECOVERY_NUMBER_ORDER_UNCOVERED");
    }

    private static LocalDate date(String value) {
        try {
            LocalDate date=LocalDate.parse(value);
            require(date.getYear()>=1000 && date.getYear()<=9999 && date.toString().equals(value),"RECOVERY_NUMBER_DATE"); return date;
        } catch(java.time.DateTimeException invalid) { throw new RecoveryControlException("RECOVERY_NUMBER_DATE",invalid); }
    }

    private static String value(JsonNode row,int index) {
        require(row.path(index).isTextual(),"RECOVERY_NUMBER_SNAPSHOT_ROW");
        try {
            String encoded=row.path(index).textValue(); byte[] bytes=Base64.getDecoder().decode(encoded);
            require(Base64.getEncoder().encodeToString(bytes).equals(encoded),"RECOVERY_NUMBER_SNAPSHOT_ROW");
            return new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch(IllegalArgumentException invalid) { throw new RecoveryControlException("RECOVERY_NUMBER_SNAPSHOT_ROW",invalid); }
    }
    private RecoveryReleaseNumberSnapshot() { }
}
