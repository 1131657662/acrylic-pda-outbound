package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
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
        jdbc.query("SELECT BIN_TO_UUID(id),display_name FROM app_user WHERE id IN ("+placeholders+")",
                (org.springframework.jdbc.core.RowCallbackHandler)rs->names.put(rs.getString(1).toLowerCase(Locale.ROOT),rs.getString(2)),parameters);
        for(String id:operators) {
            String name=names.get(id.toLowerCase(Locale.ROOT));
            if(name==null || name.isBlank() || name.codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))
                throw new BusinessException(409,"EXPORT_OPERATOR_NAME_UNAVAILABLE","扫码人缺少中文姓名，请完善人员资料后重新申请导出");
        }
        return Map.copyOf(names);
    }
}
