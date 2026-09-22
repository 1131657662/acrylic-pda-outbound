package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.reports.OperatorNames;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Current account names, read in the export acceptance transaction and frozen with the snapshot. */
@Component
public class ExportOperatorNames {
    private final JdbcTemplate jdbc;
    public ExportOperatorNames(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,String> resolve(Set<String> operators) {
        if(operators.isEmpty()) return Map.of();
        String placeholders=String.join(",",Collections.nCopies(operators.size(),"?"));
        Object[] parameters=operators.stream().map(id->Ids.bytes(UUID.fromString(id))).toArray();
        var names=new HashMap<String,String>();
        var accounts=new HashMap<String,String>();
        jdbc.query("SELECT BIN_TO_UUID(id),display_name,username FROM app_user WHERE id IN ("+placeholders+")",
                (org.springframework.jdbc.core.RowCallbackHandler)rs->{
                    String key=rs.getString(1).toLowerCase(Locale.ROOT);
                    names.put(key,rs.getString(2));accounts.put(key,rs.getString(3));
                },parameters);
        for(String id:operators) {
            String key=id.toLowerCase(Locale.ROOT);
            if(!OperatorNames.usable(names.get(key),id,accounts.get(key)))
                throw new BusinessException(409,"EXPORT_OPERATOR_NAME_UNAVAILABLE","扫码人姓名缺失或无效，请完善人员资料后重新申请导出");
        }
        return Map.copyOf(names);
    }
}
