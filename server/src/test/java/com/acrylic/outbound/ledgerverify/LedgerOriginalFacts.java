package com.acrylic.outbound.ledgerverify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Original database bytes and private files, excluding new ledger rows and mutable login facts. */
public final class LedgerOriginalFacts {
    private final JdbcTemplate jdbc;
    public LedgerOriginalFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Map<String,Object> capture(String submission) throws Exception {
        var facts=new TreeMap<String,Object>();
        facts.put("submission",rows("SELECT * FROM submission WHERE id=UUID_TO_BIN(?)",submission));
        facts.put("scope",rows("SELECT c.* FROM submission_scope c JOIN submission s ON s.scope_id=c.id WHERE s.id=UUID_TO_BIN(?)",submission));
        for(String table:List.of("submission_payload","submission_upload","submission_chunk","submitted_capture","submitted_event","bale_commitment"))
            facts.put(table,rows("SELECT * FROM "+table+" WHERE submission_id=UUID_TO_BIN(?) ORDER BY 1,2",submission));
        facts.put("authorization",rows("SELECT a.* FROM temporary_authorization a JOIN submission s ON s.temporary_authorization_id=a.id WHERE s.id=UUID_TO_BIN(?)",submission));
        facts.put("counts",jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM submission x JOIN submission_scope q ON q.id=x.scope_id WHERE q.warehouse_id=c.warehouse_id) submissions,"
            +"(SELECT COUNT(*) FROM outbound_order o WHERE o.warehouse_id=c.warehouse_id) orders,"
            +"(SELECT COUNT(*) FROM order_assignment a JOIN outbound_order o ON o.id=a.order_id WHERE o.warehouse_id=c.warehouse_id) assignments "
            +"FROM submission s JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)",submission));
        facts.put("files",files(submission));return facts;
    }

    private List<Map<String,Object>> rows(String sql,String submission) {
        return jdbc.query(sql,(rs,n)->{
            var row=new TreeMap<String,Object>();var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                byte[] bytes=rs.getBytes(i);row.put(meta.getColumnLabel(i),bytes==null?null:Base64.getEncoder().encodeToString(bytes));
            }
            return row;
        },submission);
    }

    private Map<String,Object> files(String submission) throws Exception {
        var files=new TreeMap<String,Object>();var root=LedgerVerifyEnvironment.ROOT.resolve("private-files-r2").toAbsolutePath().normalize();
        var keys=new ArrayList<String>();
        keys.addAll(jdbc.queryForList("SELECT file_key FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",String.class,submission));
        keys.addAll(jdbc.queryForList("SELECT file_key FROM submission_chunk WHERE submission_id=UUID_TO_BIN(?)",String.class,submission));
        for(String key:keys) {
            Path path=root.resolve(key).normalize();
            if(!path.startsWith(root))throw new IllegalStateException("Unexpected private file reference");
            byte[] bytes=Files.readAllBytes(path);
            files.put(key,Map.of("size",bytes.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
        }
        return files;
    }

    public void save(String name,Object value) throws Exception {
        Path path=LedgerVerifyEnvironment.evidence(name);Files.createDirectories(path.getParent());
        byte[] bytes=new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
}
