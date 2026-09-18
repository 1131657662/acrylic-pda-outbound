package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class UserManagementRollbackIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Autowired IdentityStore identities;
    @MockitoSpyBean AuditWriter audit;
    @Test void auditFailureRollsBackGrantsVersionReceiptAndSessionRevocation() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));var api=new UserManagementDriver(web);
        var body=api.createBody();String id=api.create(body).get("userId").asText();var before=api.item(id);
        var person=identities.loadUserByUsername(body.get("username").asText());
        var pda=new PdaTestDriver(new PublicationDriver(mvc,json,new IdentityFixture(person,web.identity().warehouse())));
        var tokens=pda.login(new PdaTestDriver(web).register());
        var update=api.updateBody(before).put("status","DISABLED");String key=UUID.randomUUID().toString();
        doThrow(new IllegalStateException("injected audit failure")).when(audit).append(any(),anyString());
        try { web.send(api.command("/"+id,update,key)).andExpect(status().isInternalServerError()); }
        finally { doCallRealMethod().when(audit).append(any(),anyString()); }
        assertThat(api.item(id)).isEqualTo(before);
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isOk());
        web.send(get("/api/v1/users/requests/"+key)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_command_receipt WHERE user_id=?",Integer.class,Ids.bytes(UUID.fromString(id)))).isEqualTo(1);
        assertThat(web.data(api.command("/"+id,update,key)).get("status").asText()).isEqualTo("DISABLED");
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isUnauthorized());
    }
}
