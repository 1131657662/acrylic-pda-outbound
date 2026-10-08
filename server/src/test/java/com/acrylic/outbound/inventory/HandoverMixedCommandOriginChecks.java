package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Direct database anti-forgery checks against an actual receiving command after upgrade. */
final class HandoverMixedCommandOriginChecks {
    static void verify(JdbcTemplate jdbc,UUID source) {
        var columns=jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
            +"AND TABLE_NAME='temporary_command_receipt' ORDER BY ORDINAL_POSITION",String.class);
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt",Long.class);
        for(String changed:List.of("creator_device_id","client_order_id","original_operator_id","temporary_authorization_id")) {
            var values=columns.stream().map(name->name.equals(changed)?"UUID_TO_BIN(UUID())":"`"+name+"`").toList();
            String names=String.join(",",columns.stream().map(name->"`"+name+"`").toList());
            assertThatThrownBy(()->jdbc.update("INSERT INTO temporary_command_receipt ("+names+") SELECT "+String.join(",",values)
                +" FROM temporary_command_receipt WHERE submission_id=? LIMIT 1",Ids.bytes(source)))
                .hasMessageContaining("TEMP command origin binding is invalid");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt",Long.class)).isEqualTo(count);
    }
}
