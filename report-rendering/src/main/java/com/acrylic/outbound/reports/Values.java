package com.acrylic.outbound.reports;

import java.math.*;
import java.time.*;

final class Values {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    static String text(String value) {
        require(value != null && value.length() <= 1000, "文本缺失或超过1000字符");
        require(value.codePoints().noneMatch(c -> (c < 32 && c != 10 && c != 9)
                || c == 0xfffe || c == 0xffff || (c >= 0xd800 && c <= 0xdfff)), "文本含非法控制字符");
        return value;
    }
    static String required(String value) {
        require(!text(value).isBlank(), "必填文本为空");
        return value;
    }
    static BigInteger grams(String value) {
        require(value != null && value.matches("0|[1-9][0-9]{0,29}"), "重量必须为非负整数克字符串（最多30位）");
        return new BigInteger(value);
    }
    static String kg(BigInteger grams) {
        return new BigDecimal(grams, 3).stripTrailingZeros().toPlainString();
    }
    static LocalDate shipmentDay(OrderSnapshot order) {
        return order.confirmedAt().atZone(ZONE).toLocalDate();
    }
    static String time(Instant instant) {
        return instant.atZone(ZONE).toLocalDateTime().toString().replace('T', ' ');
    }
    static boolean excelExact(BigInteger grams) {
        return grams.abs().toString().length() <= 15;
    }
    private Values() { }
}
