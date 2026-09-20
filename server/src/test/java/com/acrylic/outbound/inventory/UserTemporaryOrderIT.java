package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class UserTemporaryOrderIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords; @Autowired IdentityStore identities;
    private PublicationDriver web; private UserManagementDriver users;
    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));users=new UserManagementDriver(web);
    }
    @Test void managerCanConfigureTemporaryRolesWithoutHoldingExecutionPermissions() throws Exception {
        assertThat(identities.permissions(web.identity().user().id())).contains("temporary_order:manage")
            .doesNotContain("temporary_order:create","temporary_order:confirm","temporary_order:link");
        assertThat(web.data(get("/api/v1/users/options")).get("roles").findValuesAsText("code"))
            .contains("TEMPORARY_ORDER_CREATOR","TEMPORARY_ORDER_CONFIRMER","TEMPORARY_ORDER_LINKER");
        var operator=IdentityFixture.create(jdbc,passwords,"PDA_OPERATOR");
        assertThat(identities.permissions(operator.user().id())).doesNotContain("temporary_order:manage","temporary_order:create","temporary_order:confirm","temporary_order:link");
    }
    @ParameterizedTest @CsvSource({"TEMPORARY_ORDER_CREATOR,temporary_order:create","TEMPORARY_ORDER_CONFIRMER,temporary_order:confirm","TEMPORARY_ORDER_LINKER,temporary_order:link"})
    void temporaryExecutionRolesAreIndependentlyGrantableAndRevocable(String role,String permission) throws Exception {
        var body=users.createBody();body.putArray("roles").add(role);String id=users.create(body).get("userId").asText();
        assertThat(identities.permissions(UUID.fromString(id))).containsExactly(permission);
        assertThat(web.data(get("/api/v1/users")).get("items").findValuesAsText("id")).contains(id);
        var update=users.updateBody(users.item(id));update.putArray("roles").add("ORDER_VIEWER");
        web.data(users.command("/"+id,update,UUID.randomUUID().toString()));
        assertThat(identities.permissions(UUID.fromString(id))).doesNotContain(permission);
        assertThat(identities.permissions(web.identity().user().id())).doesNotContain(permission);
    }
    @Test void executorCannotDelegateTemporaryPermissionsEvenWithUserManagement() throws Exception {
        String id=web.identity().user().id().toString();
        jdbc.update("DELETE rp FROM role_permission rp INNER JOIN role r ON r.id=rp.role_id INNER JOIN permission p ON p.id=rp.permission_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code='temporary_order:manage'");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'",Ids.bytes(UUID.fromString(id)));
        var body=users.createBody();body.putArray("roles").add("TEMPORARY_ORDER_CONFIRMER");
        web.send(users.command("",body,UUID.randomUUID().toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_ROLE_DENIED"));
        assertThat(web.data(get("/api/v1/users/options")).get("roles").findValuesAsText("code")).doesNotContain("TEMPORARY_ORDER_CONFIRMER");
    }
    @Test void temporaryManagementDoesNotAllowUnrelatedPermissionEscalation() throws Exception {
        jdbc.update("DELETE rp FROM role_permission rp INNER JOIN role r ON r.id=rp.role_id INNER JOIN permission p ON p.id=rp.permission_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code='submission:review'");
        var body=users.createBody();body.putArray("roles").add("WAREHOUSE_ADMIN");
        UUID extra=Ids.next();jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(extra),"review-"+extra,"独立审批角色");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='submission:review'",Ids.bytes(extra));
        body.putArray("roles").add("TEMPORARY_ORDER_CONFIRMER").add("review-"+extra);
        web.send(users.command("",body,UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    @Test void temporaryRoleTargetsOutsideWarehouseRemainHidden() throws Exception {
        var body=users.createBody();body.putArray("roles").add("TEMPORARY_ORDER_CONFIRMER");String id=users.create(body).get("userId").asText();
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(UUID.fromString(id)),Ids.bytes(other.warehouse()));
        web.send(get("/api/v1/users/"+id)).andExpect(status().isNotFound());
        assertThat(web.data(get("/api/v1/users")).get("items").findValuesAsText("id")).doesNotContain(id);
    }
}
