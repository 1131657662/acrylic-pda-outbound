package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="LEDGER_VERIFY_IT",matches="true")
abstract class LedgerVerifyFixture {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) { LedgerVerifyEnvironment.jdbc(); }

    LedgerVerifySeed seed(String scenario) throws Exception {
        var value=new LedgerVerifySeed(context,scenario.equals("abort-partial")?400:0);
        if(scenario.equals("complete") || scenario.equals("abort-complete"))value.submit();
        if(scenario.equals("abort-partial"))value.partialUpload();
        if(!scenario.equals("complete"))value.abort();
        return value;
    }

    long count(String sql,Object... arguments) { return jdbc.queryForObject(sql,Long.class,arguments); }
}
