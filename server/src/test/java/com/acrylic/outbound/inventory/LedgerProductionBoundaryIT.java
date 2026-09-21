package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="LEDGER_RUNTIME_VERIFY",matches="true")
@ContextConfiguration(initializers=LedgerRuntimeEnvironment.class)
class LedgerProductionBoundaryIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired TemporaryCommandService commands;@Autowired CommandFacts facts;
    @Autowired org.springframework.beans.factory.ObjectProvider<CommandOperation> operations;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void applicationHasNoCommandHandlerAndNewAttemptCreatesNoLedgerRows() throws Exception {
        assertThat(operations.stream()).isEmpty();
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'",web.identity().user().id().toString());
        var fixture=new LedgerAuthorFixture(new TemporaryFixture(web,jdbc));fixture.web();var command=fixture.command(facts,"NORMAL_CONFIRM");
        assertThatThrownBy(()->commands.execute(command.toString().getBytes(StandardCharsets.UTF_8),"production-boundary"))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("COMMAND_EXECUTION_NOT_IMPLEMENTED"));
        for(String table:List.of("request","receipt","event","audit"))assertThat(fixture.count(table)).isZero();
        assertThat(facts.read(UUID.fromString(fixture.submission)).text("decisionVersion")).isEqualTo("0");
    }
    @Test void onlyFourGetRoutesExposeTheLedger() {
        var paths=new HashSet<String>();
        mappings.getHandlerMethods().forEach((mapping,method)->{
            if(method.getBeanType()==CommandReadController.class) {
                assertThat(mapping.getMethodsCondition().getMethods()).containsExactly(RequestMethod.GET);
                paths.addAll(mapping.getPatternValues());
            }
        });
        assertThat(paths).containsExactlyInAnyOrder("/api/v1/temporary-command-receipts/{commandId}",
            "/api/v1/temporary-command-receipts/{commandId}/original","/api/v1/pda/temporary-command-receipts/{commandId}",
            "/api/v1/pda/temporary-command-receipts/{commandId}/original");
    }
}
