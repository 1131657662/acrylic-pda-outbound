package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class UserManagementIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords; @Autowired IdentityStore identities;
    private PublicationDriver web; private UserManagementDriver users;
    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));users=new UserManagementDriver(web);
    }
    @Test void createReplayAndLookupNeverReturnPasswordMaterial() throws Exception {
        var body=users.createBody();String key=UUID.randomUUID().toString();var receipt=web.data(users.command("",body,key));
        assertThat(web.data(users.command("",body,key))).isEqualTo(receipt);
        assertThat(web.data(get("/api/v1/users/requests/"+key))).isEqualTo(receipt);
        web.send(users.command("",body.deepCopy().put("password","Different-password-123"),key)).andExpect(status().isConflict());
        String id=receipt.get("userId").asText();var item=users.item(id);
        assertThat(item.get("roles").get(0).asText()).isEqualTo("PDA_OPERATOR");
        String hash=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,Ids.bytes(UUID.fromString(id)));
        assertThat(passwords.matches(body.get("password").asText(),hash)).isTrue();
        String audit=jdbc.queryForObject("SELECT CAST(details AS CHAR) FROM operation_event WHERE entity_id=? AND event_type='USER_CREATE'",String.class,Ids.bytes(UUID.fromString(id)));
        assertThat(receipt.toString()+item+audit+web.data(get("/api/v1/users/"+id+"/history"))).doesNotContain(body.get("password").asText(),hash,"password_verifier");
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(get("/api/v1/users/requests/"+key)).andExpect(status().isNotFound());
    }
    @Test void updateAndPasswordReplayKeepTheOriginalReceiptWithoutRevertingNewerData() throws Exception {
        var body=users.createBody();String id=users.create(body).get("userId").asText();var update=users.updateBody(users.item(id)).put("status","DISABLED");
        String key=UUID.randomUUID().toString();var disabled=web.data(users.command("/"+id,update,key));
        var reset=json.createObjectNode().put("expectedVersion","2").put("password","Replacement-password-123").put("reason","按申请重置");
        var passwordReceipt=web.data(users.command("/"+id+"/password",reset,"password-"+key));
        assertThat(web.data(users.command("/"+id+"/password",reset,"password-"+key))).isEqualTo(passwordReceipt);
        assertThat(web.data(users.command("/"+id,update,key))).isEqualTo(disabled);
        assertThat(users.item(id).get("rowVersion").asText()).isEqualTo("3");
        web.send(users.command("/"+id,update,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_VERSION_CONFLICT"));
    }
    @Test void revokedWebAndPdaSessionsDoNotReleaseOrRewriteOriginalAssignment() throws Exception {
        var body=users.createBody();String id=users.create(body).get("userId").asText();
        var person=identities.loadUserByUsername(body.get("username").asText());
        var pda=new PdaTestDriver(new PublicationDriver(mvc,json,new IdentityFixture(person,web.identity().warehouse())));
        var adminPda=new PdaTestDriver(web);var device=adminPda.register();var tokens=pda.login(device);
        String order=adminPda.readyOrder();String key=UUID.randomUUID().toString();var assignment=pda.data(pda.claim(order,key),tokens);
        var update=users.updateBody(users.item(id)).put("displayName","姓名已核对");
        web.data(users.command("/"+id,update,UUID.randomUUID().toString()));
        mvc.perform(get("/api/v1/me").with(user(person))).andExpect(status().isUnauthorized());
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isUnauthorized());
        var restored=pda.data(pda.claim(order,key),pda.login(device));
        assertThat(restored.get("offlineToken")).isEqualTo(assignment.get("offlineToken"));
        assertThat(restored.get("expiresAt")).isEqualTo(assignment.get("expiresAt"));
        assertThat(restored.get("id")).isEqualTo(assignment.get("id"));
        var competing=adminPda.login(adminPda.register());
        adminPda.send(adminPda.claim(order,UUID.randomUUID().toString()),competing).andExpect(status().isConflict());
    }
    @Test void multiWarehouseTargetsOutsideAdministratorScopeAreHiddenAndProtected() throws Exception {
        String id=users.create(users.createBody()).get("userId").asText();var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var update=users.updateBody(users.item(id));update.withArray("warehouseIds").add(other.warehouse().toString());
        web.send(users.command("/"+id,update,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(UUID.fromString(id)),Ids.bytes(other.warehouse()));
        web.send(get("/api/v1/users/"+id)).andExpect(status().isNotFound());
        web.send(get("/api/v1/users/"+id+"/history")).andExpect(status().isNotFound());
        web.send(users.command("/"+id,update,UUID.randomUUID().toString())).andExpect(status().isNotFound());
        assertThat(web.data(get("/api/v1/users")).get("items").findValuesAsText("id")).doesNotContain(id);
    }
    @Test void administratorsCannotChangeTheirOwnManagementOrGrantUnknownRoles() throws Exception {
        String self=web.identity().user().id().toString();var change=users.updateBody(users.item(self)).put("status","DISABLED");
        web.send(users.command("/"+self,change,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_SELF_CHANGE_DENIED"));
        var create=users.createBody();create.putArray("roles").add("invented-admin");
        web.send(users.command("",create,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(identities.loadUserByUsername(web.identity().user().username()).active()).isTrue();
    }
    @Test void rolePermissionsCannotExceedTheActingAdministrator() throws Exception {
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(web.identity().user().id()));
        UUID role=Ids.next();jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"SCOPED-"+role,"受限人员管理员");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='user:manage'",Ids.bytes(role));
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(web.identity().user().id()),Ids.bytes(role));
        web.send(users.command("",users.createBody(),UUID.randomUUID().toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_ROLE_DENIED"));
        assertThat(web.data(get("/api/v1/users/options")).get("roles").findValuesAsText("code")).doesNotContain("WAREHOUSE_ADMIN","PDA_OPERATOR");
    }
    @Test void queriesUseTrueTotalsStablePagesAndExactUsername() throws Exception {
        for(int i=0;i<22;i++) users.create(users.createBody());
        var first=web.data(get("/api/v1/users"));var second=web.data(get("/api/v1/users").param("page","2"));
        assertThat(first.get("total").asInt()).isEqualTo(23);assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(second.get("items").size()).isEqualTo(3);
        var firstIds=new ArrayList<String>();first.get("items").forEach(item->firstIds.add(item.get("id").asText()));
        var secondIds=new ArrayList<String>();second.get("items").forEach(item->secondIds.add(item.get("id").asText()));
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        var exact=web.data(get("/api/v1/users").param("username",web.identity().user().username()));assertThat(exact.get("total").asInt()).isEqualTo(1);
        assertThat(web.data(get("/api/v1/users").param("page","999")).get("page").asInt()).isEqualTo(2);
        web.send(get("/api/v1/users").param("pageSize","100000")).andExpect(status().isBadRequest());
    }
    @Test void commandsRequireCsrfPermissionEpochAndValidPassword() throws Exception {
        var body=users.createBody();String key=UUID.randomUUID().toString();
        mvc.perform(users.command("",body,key).param("warehouseId",web.identity().warehouse().toString()).with(user(web.identity().user()))
            .header("X-Server-Recovery-Epoch","1")).andExpect(status().isForbidden());
        mvc.perform(users.command("",body,key).param("warehouseId",web.identity().warehouse().toString()).with(user(web.identity().user())).with(csrf())
            .header("X-Server-Recovery-Epoch","2")).andExpect(status().isConflict());
        var viewer=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER"));
        viewer.send(get("/api/v1/users")).andExpect(status().isForbidden());viewer.send(users.command("",body,key)).andExpect(status().isForbidden());
        web.send(users.command("",body.deepCopy().put("password","short7x"),key)).andExpect(status().isBadRequest());
        web.send(users.command("",body.deepCopy().put("password","密".repeat(30)),key)).andExpect(status().isBadRequest());
        body.putArray("roles").add("PDA_OPERATOR").add("PDA_OPERATOR");web.send(users.command("",body,key)).andExpect(status().isBadRequest());
    }
    @Test void selfPasswordChangeInvalidatesOldWebSessionAndCanRecoverAfterLogin() throws Exception {
        String id=web.identity().user().id().toString();String key=UUID.randomUUID().toString();
        var body=json.createObjectNode().put("expectedVersion","1").put("password","Self-replacement-123").put("reason","本人修改密码");
        var original=web.data(users.command("/"+id+"/password",body,key));
        web.send(get("/api/v1/me")).andExpect(status().isUnauthorized());
        var current=identities.loadUserByUsername(web.identity().user().username());assertThat(passwords.matches(body.get("password").asText(),current.getPassword())).isTrue();
        var refreshed=new PublicationDriver(mvc,json,new IdentityFixture(current,web.identity().warehouse()));
        assertThat(refreshed.data(get("/api/v1/users/requests/"+key))).isEqualTo(original);
    }
}
