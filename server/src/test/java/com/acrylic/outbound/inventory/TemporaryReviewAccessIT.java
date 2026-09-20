package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.review.ReviewReminderStore;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryReviewAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired ReviewReminderStore reminders;
    private TemporaryReviewFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryReviewFixture(jdbc,mvc,json,passwords);f.temporary.submit(); }
    @ParameterizedTest @ValueSource(strings={"TEMP","FORMAL","BOTH","NONE"})
    void reviewAndReminderPermissionsRemainIndependent(String quadrant) throws Exception {
        var roles=switch(quadrant) {case "TEMP"->new String[]{"TEMPORARY_ORDER_CONFIRMER"};case "FORMAL"->new String[]{"WAREHOUSE_ADMIN"};
            case "BOTH"->new String[]{"WAREHOUSE_ADMIN","TEMPORARY_ORDER_CONFIRMER"};default->new String[]{};};
        var reader=f.reader(roles);var formal=new SubmissionFixture(f.temporary.f);formal.body.put("shortShipmentReason","测试短装");
        // A full plan can still wait for review when its bales are reserved by the temporary batch.
        assertThat(formal.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        jdbc.update("UPDATE submission SET created_at=UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE WHERE id IN (UUID_TO_BIN(?),UUID_TO_BIN(?))",f.temporary.id(),formal.id());
        assertThat(reminders.createDueReminders(reader.identity().warehouse())).isEqualTo(2);
        assertThat(reminders.createDueReminders(reader.identity().warehouse())).isZero();
        boolean temp=quadrant.equals("TEMP") || quadrant.equals("BOTH");
        reader.send(get(f.path())).andExpect(temp?status().isOk():status().isForbidden());
        reader.send(get(f.path()+"/scans")).andExpect(temp?status().isOk():status().isForbidden());
        reader.send(get("/api/v1/temporary-reviews")).andExpect(temp?status().isOk():status().isForbidden());
        if(quadrant.equals("NONE")) { reader.send(get("/api/v1/review-reminders")).andExpect(status().isForbidden());return; }
        var items=reader.data(get("/api/v1/review-reminders")).path("items");var sources=new ArrayList<String>();
        items.forEach(row->sources.add(row.path("sourceType").asText()));
        assertThat(sources).containsExactlyInAnyOrder(quadrant.equals("BOTH")?new String[]{"FORMAL","TEMP"}:new String[]{quadrant});
    }
    @Test void warehouseAndObjectScopesAreCheckedBeforeReturningEvidence() throws Exception {
        var outsider=IdentityFixture.create(jdbc,passwords,"TEMPORARY_ORDER_CONFIRMER");
        mvc.perform(get(f.path()).param("warehouseId",f.reviewer.identity().warehouse().toString()).with(user(outsider.user()))).andExpect(status().isNotFound());
        mvc.perform(get(f.path()).param("warehouseId",outsider.warehouse().toString()).with(user(outsider.user()))).andExpect(status().isNotFound());
        mvc.perform(get(f.path()+"/scans").param("warehouseId",outsider.warehouse().toString()).with(user(outsider.user()))).andExpect(status().isNotFound());
        f.reviewer.send(get("/api/v1/temporary-reviews/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get(f.path()).param("warehouseId",f.reviewer.identity().warehouse().toString())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"VERSION","DISABLED","REVOKED"}) void changedReviewerAccessIsRecheckedOnEveryRead(String change) throws Exception {
        UUID id=f.reviewer.identity().user().id();
        switch(change) {
            case "VERSION"->jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",Ids.bytes(id));
            case "DISABLED"->jdbc.update("UPDATE app_user SET status='DISABLED' WHERE id=?",Ids.bytes(id));
            default->jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(id));
        }
        f.reviewer.send(get(f.path())).andExpect(change.equals("REVOKED")?status().isForbidden():status().isUnauthorized());
        f.reviewer.send(get("/api/v1/review-reminders")).andExpect(change.equals("REVOKED")?status().isForbidden():status().isUnauthorized());
    }
    @Test void terminalTemporaryReminderDisappearsWithoutDeletingItsHistory() throws Exception {
        jdbc.update("UPDATE submission SET created_at=UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE WHERE id=UUID_TO_BIN(?)",f.temporary.id());
        reminders.createDueReminders(f.reviewer.identity().warehouse());f.temporary.abort();
        assertThat(f.reviewer.data(get("/api/v1/review-reminders")).path("items")).isEmpty();
        assertThat(reminders.list(f.reviewer.identity().warehouse(),20)).hasSize(1);
    }
}
