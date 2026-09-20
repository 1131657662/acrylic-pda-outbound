package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class OrderNumbers {
    private final JdbcTemplate jdbc;
    public OrderNumbers(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(propagation=Propagation.MANDATORY)
    public String next(Instant createdAt) {
        LocalDate date=createdAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        jdbc.update("""
            INSERT INTO order_number_counter(business_date,last_sequence) VALUES(?,0)
            ON DUPLICATE KEY UPDATE business_date=business_date
            """,date);
        int previous=jdbc.queryForObject("SELECT last_sequence FROM order_number_counter WHERE business_date=? FOR UPDATE",Integer.class,date);
        if(previous==Integer.MAX_VALUE) throw new BusinessException(409,"ORDER_NUMBER_EXHAUSTED","当日订单编号已达上限，请联系管理员");
        int next=previous+1;
        jdbc.update("UPDATE order_number_counter SET last_sequence=? WHERE business_date=?",next,date);
        return "FH"+date.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%04d",next);
    }
}
