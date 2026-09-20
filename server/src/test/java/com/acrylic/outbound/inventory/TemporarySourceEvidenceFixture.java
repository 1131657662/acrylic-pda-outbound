package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

final class TemporarySourceEvidenceFixture {
    final TemporarySubmissionFixture temporary;final PublicationDriver reviewer;final JdbcTemplate jdbc;final Path storage;
    TemporarySourceEvidenceFixture(Inputs inputs,boolean large) throws Exception {
        jdbc=inputs.jdbc();storage=Path.of(inputs.storage());
        var owner=new PublicationDriver(inputs.mvc(),inputs.json(),IdentityFixture.create(jdbc,inputs.passwords(),"WAREHOUSE_ADMIN"));
        var formal=new SubmissionFixture(owner,jdbc,false,false);if(large)formal.addAcknowledgedFailures(400);
        temporary=new TemporarySubmissionFixture(formal);
        var identity=IdentityFixture.create(jdbc,inputs.passwords(),"TEMPORARY_ORDER_CONFIRMER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(owner.identity().warehouse()));
        reviewer=new PublicationDriver(inputs.mvc(),inputs.json(),new IdentityFixture(identity.user(),owner.identity().warehouse()));
    }
    String path() { return "/api/v1/temporary-source-evidence/"+temporary.id(); }
    MockHttpServletRequestBuilder request(String suffix) {
        return get(path()+suffix).param("creatorDeviceId",temporary.a.device()).param("clientOrderId",temporary.a.client).param("workRound","1");
    }
    JsonNode read() throws Exception { return reviewer.data(request("")); }
    byte[] begin() throws Exception {
        byte[] bytes=SubmissionJson.canonical(temporary.body).getBytes(StandardCharsets.UTF_8);
        var c=temporary.control().put("payloadSha256",SubmissionJson.sha(temporary.body)).put("byteCount",Integer.toString(bytes.length))
            .put("chunkCount",Integer.toString((bytes.length+262143)/262144));
        temporary.f.pda.data(temporary.request(temporary.base()+"/upload",c),temporary.f.tokens);return bytes;
    }
    void chunk(int index,byte[] bytes) throws Exception {
        temporary.f.pda.data(post(temporary.base()+"/chunks/"+index).header("X-Server-Recovery-Epoch","1")
            .contentType("application/octet-stream").content(bytes),temporary.f.tokens);
    }
    String storedReceipt() { return jdbc.queryForObject("SELECT receipt_text FROM submission WHERE id=UUID_TO_BIN(?)",String.class,temporary.id()); }
    Map<String,Object> facts() throws Exception {
        var result=new TreeMap<String,Object>();
        for(String table:jdbc.queryForList("SHOW TABLES",String.class)) {
            if(table.equals("spring_session") || table.equals("spring_session_attributes"))continue;
            if(!table.matches("[a-z_]+"))throw new IllegalStateException("Unexpected table identifier");
            result.put(table,jdbc.queryForList("CHECKSUM TABLE `"+table+"`"));
        }
        var files=new TreeMap<String,String>();
        try(var paths=Files.walk(storage)) {
            for(var path:paths.filter(Files::isRegularFile).toList())files.put(storage.relativize(path).toString(),
                com.acrylic.outbound.inventory.ImportFiles.sha256(Files.readAllBytes(path)));
        }
        result.put("privateFiles",files);return result;
    }
    record Inputs(JdbcTemplate jdbc,MockMvc mvc,ObjectMapper json,PasswordEncoder passwords,String storage) { }
}
