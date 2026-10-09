package com.acrylic.outbound.recovery;

import java.util.*;
import java.util.zip.CRC32;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Bounded MySQL 8.0 binlog prefix framing with CRC32; it never executes SQL or trusts text dumps. */
final class RecoveryBinlogFile {
    static final int MAX_BYTES = 64 * 1024 * 1024;
    private RecoveryBinlogFile() { }

    static List<Event> read(byte[] bytes) {
        require(bytes != null && bytes.length >= 4 && bytes.length <= MAX_BYTES, "RECOVERY_BINLOG_SIZE");
        require(Arrays.equals(Arrays.copyOf(bytes, 4), new byte[]{(byte)0xfe, 'b', 'i', 'n'}), "RECOVERY_BINLOG_MAGIC");
        var events = new ArrayList<Event>(); int offset = 4; long server = 0;
        while (offset < bytes.length) {
            require(events.size() < 262144, "RECOVERY_BINLOG_EVENT_LIMIT");
            require(bytes.length - offset >= 19, "RECOVERY_BINLOG_TRUNCATED");
            long size = number(bytes, offset + 9, 4), next = number(bytes, offset + 13, 4);
            require(size >= 23 && size <= bytes.length - offset && next == offset + size, "RECOVERY_BINLOG_POSITION");
            int end = Math.toIntExact(next); checksum(bytes, offset, end);
            long writer = number(bytes, offset + 5, 4);
            require(writer > 0 && (server == 0 || server == writer), "RECOVERY_BINLOG_SERVER"); server = writer;
            events.add(new Event(offset, end, bytes[offset + 4] & 255, Arrays.copyOfRange(bytes, offset + 19, end - 4)));
            offset = end;
        }
        require(!events.isEmpty() && events.getFirst().type() == 15, "RECOVERY_BINLOG_FORMAT"); format(events.getFirst());
        return List.copyOf(events);
    }

    private static void checksum(byte[] bytes, int start, int end) {
        var crc = new CRC32(); int length = end - start - 4;
        if ((bytes[start + 4] & 255) == 15) {
            require((number(bytes, start + 17, 2) & ~1L) == 0, "RECOVERY_BINLOG_FORMAT_FLAGS");
            // MySQL changes BINLOG_IN_USE after calculating the format event checksum.
            byte[] format = Arrays.copyOfRange(bytes, start, end - 4); format[17] &= (byte)~1;
            crc.update(format);
        } else crc.update(bytes, start, length);
        require(crc.getValue() == number(bytes, end - 4, 4), "RECOVERY_BINLOG_CHECKSUM");
    }

    private static void format(Event event) {
        byte[] body = event.body();
        require(body.length > 57 && number(body, 0, 2) == 4 && body[56] == 19 && body[body.length - 1] == 1,
            "RECOVERY_BINLOG_FORMAT");
        String version = ascii(body, 2, 50).replaceAll("\\x00.*", "");
        require(version.matches("8\\.0\\.[0-9]+.*"), "RECOVERY_BINLOG_VERSION");
        require(body.length > 57 + 34 && body[57 + 18] == 8 && body[57 + 29] == 10
            && body[57 + 30] == 10 && body[57 + 31] == 10, "RECOVERY_BINLOG_HEADER_LENGTHS");
    }

    static long number(byte[] bytes, int offset, int length) {
        require(offset >= 0 && length > 0 && length <= 8 && offset <= bytes.length - length, "RECOVERY_BINLOG_TRUNCATED");
        long value = 0;
        for (int index = 0; index < length; index++) value |= (bytes[offset + index] & 255L) << (index * 8);
        require(value >= 0, "RECOVERY_BINLOG_NUMBER"); return value;
    }

    static String ascii(byte[] bytes, int offset, int length) {
        require(offset >= 0 && length >= 0 && offset <= bytes.length - length, "RECOVERY_BINLOG_TRUNCATED");
        for (int index = offset; index < offset + length; index++)
            require((bytes[index] & 255) < 128, "RECOVERY_BINLOG_TEXT");
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    record Event(int start, int end, int type, byte[] body) { }
}
