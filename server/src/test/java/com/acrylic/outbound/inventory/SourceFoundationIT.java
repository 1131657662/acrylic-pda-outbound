package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest @AutoConfigureMockMvc
class SourceFoundationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    TemporarySubmissionFixture t;
    @BeforeEach void create() throws Exception {
        t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
    }
    @Test void normalIntakePersistsSourceAndObservedMappingWithoutDecision() throws Exception {
        t.submit();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment k JOIN submission s ON s.id=k.submission_id WHERE s.id=UUID_TO_BIN(?) AND k.source_scope_id=s.scope_id AND k.source_warehouse_id IS NOT NULL",Integer.class,t.id())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT m.origin FROM temporary_source_mapping m JOIN submission s ON s.scope_id=m.scope_id WHERE s.id=UUID_TO_BIN(?) ORDER BY m.mapping_version",String.class,t.id())).containsExactly("SOURCE_REGISTERED","INITIAL_ACCEPTANCE");
        assertThat(jdbc.queryForObject("SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id WHERE s.id=UUID_TO_BIN(?)",Long.class,t.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt",Integer.class)).isZero();
    }
    @Test void sourceReservationConflictsWithFormalSubmitAndLeavesOriginalAbort() throws Exception {
        var abort=t.abort();String set=SourceFoundationFixture.reserve(t,null);
        var result=new SubmissionFixture(t.f).submit();
        assertThat(result.get("issues").toString()).contains("BALE_ALREADY_COMMITTED");
        assertThat(t.read()).isEqualTo(abort);assertThat(t.orders()).isZero();
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(source_evidence_set_id) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,t.body.at("/scans/0/baleId").asText())).isEqualTo(set);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sourceOwnerNeverBecomesFormalRecoveryOwnerEvenWithMatchingOrder(boolean sameOrder) throws Exception {
        t.abort();String set=SourceFoundationFixture.reserve(t,sameOrder?t.f.order:null);
        var recall=new RecallFixture(t.f);recall.start(false);var result=recall.upload();
        assertThat(result.toString()).contains("BALE_ALREADY_COMMITTED");
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(source_evidence_set_id) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,t.body.at("/scans/0/baleId").asText())).isEqualTo(set);
    }
    @Test void ownerChecksRejectNullLegacyOrderAndMixedSourceOwner() throws Exception {
        t.submit();String bale=t.body.at("/scans/0/baleId").asText();
        assertThatThrownBy(()->jdbc.update("UPDATE bale_commitment SET order_id=NULL WHERE bale_id=UUID_TO_BIN(?)",bale)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE bale_commitment SET state='SOURCE_RESERVED' WHERE bale_id=UUID_TO_BIN(?)",bale)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
