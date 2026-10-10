package com.acrylic.outbound.recovery;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Arrays;
import static com.acrylic.outbound.recovery.RecoveryPermissionLogFixture.put;

/** Real-format two-column row images within the constrained permission fixture's original interval. */
final class RecoveryNumberBinlogFixture {
    final RecoveryControlledPermissionFixture permission=new RecoveryControlledPermissionFixture();
    final LocalDate date=LocalDate.of(2026,10,10);
    int dateType=10,columnsBitmap=3,nullBitmap=0;
    boolean truncate;

    RecoveryPermissionContinuity.Request request() {
        var log=permission.logged;
        log.end.put("binlogPosition",Integer.toString(log.log.size())); permission.publish(); return log.request();
    }

    RecoveryNumberBinlogFixture insert(int value) { return event(30,0,value,date); }
    RecoveryNumberBinlogFixture update(int before,int after) { return event(31,before,after,date); }

    RecoveryNumberBinlogFixture event(int type,int before,int after,LocalDate nextDate) {
        var log=permission.logged; byte[] gtid=new byte[56]; gtid[0]=1; gtid[25]=2; put(gtid,42,log.micros,7);
        log.event(34,gtid); log.query("BEGIN"); log.event(19,table());
        var rows=new ByteArrayOutputStream(); rows.writeBytes(new byte[]{1,0,0,0,0,0,1,0,2,0,2,(byte)columnsBitmap});
        if(type==31) { rows.write(columnsBitmap); rows.writeBytes(row(date,before)); }
        rows.writeBytes(row(nextDate,after)); byte[] bytes=rows.toByteArray();
        log.event(type,truncate?Arrays.copyOf(bytes,bytes.length-1):bytes);
        byte[] xid=new byte[8]; xid[0]=1; log.event(16,xid); return this;
    }

    private byte[] table() {
        var body=new ByteArrayOutputStream(); String schema=permission.logged.schema,table="order_number_counter";
        body.writeBytes(new byte[]{1,0,0,0,0,0,0,0}); body.write(schema.length());
        body.writeBytes(schema.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); body.write(0); body.write(table.length());
        body.writeBytes(table.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); body.write(0);
        body.writeBytes(new byte[]{2,(byte)dateType,3,0,0}); return body.toByteArray();
    }

    private byte[] row(LocalDate day,int value) {
        byte[] bytes=new byte[8]; bytes[0]=(byte)nullBitmap;
        put(bytes,1,(day.getYear()<<9)|(day.getMonthValue()<<5)|day.getDayOfMonth(),3); put(bytes,4,value,4); return bytes;
    }
}
