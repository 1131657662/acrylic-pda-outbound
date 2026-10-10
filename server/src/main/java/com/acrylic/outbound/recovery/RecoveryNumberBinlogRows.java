package com.acrylic.outbound.recovery;

import java.time.LocalDate;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;
import static com.acrylic.outbound.recovery.RecoveryBinlogFile.*;

/** Replays every global DATE/INT counter row image; missing, shrinking or deleted allocations fail closed. */
final class RecoveryNumberBinlogRows {
    private final TreeMap<LocalDate,Integer> counters;
    private final Set<Long> counterTables=new HashSet<>();
    private long allocations;
    private RecoveryNumberBinlogRows(Map<LocalDate,Integer> initial) { counters=new TreeMap<>(initial); }

    static Result replay(RecoveryPermissionContinuity.Request request,Map<LocalDate,Integer> initial) {
        RecoveryPermissionContinuity.verifyControlled(request);
        int start=Math.toIntExact(RecoveryControlFormat.revision(RecoveryControlFormat.readCanonical(request.begin()),"binlogPosition"));
        var replay=new RecoveryNumberBinlogRows(initial);
        for(var event:read(request.binlog())) if(event.start()>=start) replay.consume(event);
        return new Result(Collections.unmodifiableSortedMap(replay.counters),replay.allocations);
    }

    private void consume(Event event) {
        if(event.type()==19) table(event.body());
        if(Set.of(30,31,32).contains(event.type())) {
            if(counterTables.contains(number(event.body(),0,6))) rows(event);
            if((number(event.body(),6,2)&1)!=0) counterTables.clear();
        }
        if(Set.of(16,33,34).contains(event.type())) counterTables.clear();
    }

    private void table(byte[] body) {
        long id=number(body,0,6); counterTables.remove(id);
        int tableOffset=10+Math.toIntExact(number(body,8,1));
        int length=Math.toIntExact(number(body,tableOffset,1));
        if(!ascii(body,tableOffset+1,length).equals("order_number_counter")) return;
        int offset=tableOffset+length+2;
        require(number(body,offset,1)==2 && number(body,offset+1,1)==10 && number(body,offset+2,1)==3
            && number(body,offset+3,1)==0 && number(body,offset+4,1)==0,"RECOVERY_NUMBER_BINLOG_LAYOUT");
        counterTables.add(id);
    }

    private void rows(Event event) {
        require(event.type()!=32,"RECOVERY_NUMBER_BINLOG_DELETE"); byte[] body=event.body();
        int offset=8+Math.toIntExact(number(body,8,2));
        // MySQL may set padding bits above the two declared columns. Both actual columns must be present.
        require(number(body,offset,1)==2 && (number(body,offset+1,1)&3)==3,"RECOVERY_NUMBER_BINLOG_IMAGE"); offset+=2;
        if(event.type()==31) { require((number(body,offset,1)&3)==3,"RECOVERY_NUMBER_BINLOG_IMAGE"); offset++; }
        require(offset<body.length,"RECOVERY_NUMBER_BINLOG_IMAGE");
        while(offset<body.length) {
            Counter before=counter(body,offset); offset+=8;
            if(event.type()==30) insert(before);
            else { Counter after=counter(body,offset); offset+=8; update(before,after); }
        }
        require(offset==body.length,"RECOVERY_NUMBER_BINLOG_IMAGE");
    }

    private static Counter counter(byte[] body,int offset) {
        require((number(body,offset,1)&3)==0,"RECOVERY_NUMBER_BINLOG_NULL");
        long date=number(body,offset+1,3),sequence=number(body,offset+4,4);
        require(sequence<=Integer.MAX_VALUE,"RECOVERY_NUMBER_BINLOG_SEQUENCE");
        try {
            var day=LocalDate.of(Math.toIntExact(date>>9),(int)(date>>5)&15,(int)date&31);
            require(day.getYear()>=1000 && day.getYear()<=9999,"RECOVERY_NUMBER_DATE");
            return new Counter(day,(int)sequence);
        } catch(java.time.DateTimeException invalid) { throw new RecoveryControlException("RECOVERY_NUMBER_DATE",invalid); }
    }

    private void insert(Counter value) {
        require(value.sequence()==0 && !counters.containsKey(value.date()),"RECOVERY_NUMBER_BINLOG_INSERT");
        require(counters.size()<65536,"RECOVERY_NUMBER_BINLOG_LIMIT"); counters.put(value.date(),0);
    }

    private void update(Counter before,Counter after) {
        require(before.date().equals(after.date()) && Objects.equals(counters.get(before.date()),before.sequence())
            && before.sequence()<Integer.MAX_VALUE && after.sequence()==before.sequence()+1,"RECOVERY_NUMBER_BINLOG_DISCONTINUITY");
        counters.put(after.date(),after.sequence()); allocations++;
    }
    record Result(SortedMap<LocalDate,Integer> counters,long allocations) { }
    private record Counter(LocalDate date,int sequence) { }
}
