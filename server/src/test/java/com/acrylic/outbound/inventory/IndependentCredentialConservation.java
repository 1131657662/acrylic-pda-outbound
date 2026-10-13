package com.acrylic.outbound.inventory;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Whole-table checksums include every old business value, including non-empty seeded records. */
final class IndependentCredentialConservation {
    private IndependentCredentialConservation() { }
    static Map<String,Fact> snapshot(JdbcTemplate jdbc) {
        var names=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() " +
            "AND table_type='BASE TABLE' ORDER BY table_name",String.class);
        var result=new TreeMap<String,Fact>();
        for(String name:names) {
            if(!name.matches("(bale|inventory|stock|submission|order|outbound_order|temporary|pc|scan|correction|export)(_[a-z0-9]+)*"))continue;
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM `"+name+"`",Long.class);
            Long checksum=jdbc.queryForObject("CHECKSUM TABLE `"+name+"` EXTENDED",(rs,n)->(Long)rs.getObject(2));
            assertThat(checksum).as("checksum of %s",name).isNotNull();result.put(name,new Fact(count,checksum));
        }
        assertThat(result).containsKeys("bale","submission","outbound_order","bale_commitment");
        assertThat(result.values().stream().mapToLong(Fact::rows).sum()).as("non-empty business baseline").isPositive();
        return result;
    }
    record Fact(long rows,long checksum) { }
}
