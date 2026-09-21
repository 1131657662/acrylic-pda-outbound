package com.acrylic.outbound.temporary.command;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LedgerRuntimeEnvironmentTest {
    private Map<String,String> environment(boolean combined) {
        String root=combined?"D:/acrylic-pda-worktrees/sixth-wave-integration/.local":"D:/acrylic-pda-worktrees/r06-ledger-runtime/.local";
        String database=combined?"acrylic_outbound_wave6_20260921":"acrylic_outbound_ledger_20260921";
        return new HashMap<>(Map.of("LEDGER_RUNTIME_VERIFY","true","OUTBOUND_MIGRATE","true",
            "OUTBOUND_DB_URL","jdbc:mysql://127.0.0.1:"+(combined?19328:19326)+"/"+database+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true",
            "OUTBOUND_DB_USER",combined?"outbound_wave6_20260921":"outbound_ledger_20260921","OUTBOUND_STORAGE",root+"/private-files",
            "OUTBOUND_AUTH_SIGNING_PRIVATE_KEY",root+"/signing/"+(combined?"private.der":"private.pk8"),"OUTBOUND_AUTH_SIGNING_PUBLIC_KEY",root+"/signing/public.der"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allowsOnlyWholeAssignedProfile(boolean combined) {
        var profile=LedgerRuntimeEnvironment.validate(environment(combined));
        assertThat(profile.port()).isEqualTo(combined?19328:19326);
        assertThatCode(()->LedgerRuntimeEnvironment.connected(profile,profile.datadir()+"/",profile.port(),profile.database(),profile.user()+"@127.0.0.1"))
            .doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"LEDGER_RUNTIME_VERIFY","OUTBOUND_MIGRATE","OUTBOUND_DB_URL","OUTBOUND_DB_USER",
        "OUTBOUND_STORAGE","OUTBOUND_AUTH_SIGNING_PRIVATE_KEY","OUTBOUND_AUTH_SIGNING_PUBLIC_KEY"})
    void missingSettingCannotEnableEitherEnvironment(String field) {
        for(boolean combined:List.of(false,true)) {
            var env=environment(combined);env.remove(field);
            assertThatThrownBy(()->LedgerRuntimeEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"OUTBOUND_DB_URL","OUTBOUND_DB_USER","OUTBOUND_STORAGE","OUTBOUND_AUTH_SIGNING_PRIVATE_KEY","OUTBOUND_AUTH_SIGNING_PUBLIC_KEY"})
    void neverMixesAuthorAndCombinationResources(String field) {
        for(boolean combined:List.of(false,true)) {
            var env=environment(combined);env.put(field,environment(!combined).get(field));
            assertThatThrownBy(()->LedgerRuntimeEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"13316","19321","19322","19323","19324","19325","19327","19329"})
    void refusesUnassignedPorts(String port) {
        var env=environment(true);env.put("OUTBOUND_DB_URL",env.get("OUTBOUND_DB_URL").replace("19328",port));
        assertThatThrownBy(()->LedgerRuntimeEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesUnassignedPathNamespaceHostAndExtraJdbcOptions() {
        for(String field:List.of("OUTBOUND_STORAGE","OUTBOUND_AUTH_SIGNING_PRIVATE_KEY","OUTBOUND_AUTH_SIGNING_PUBLIC_KEY","OUTBOUND_DB_URL","OUTBOUND_DB_USER")) {
            var env=environment(true);env.put(field,env.get(field)+"-other");
            assertThatThrownBy(()->LedgerRuntimeEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
        }
        var env=environment(true);env.put("OUTBOUND_DB_URL",env.get("OUTBOUND_DB_URL").replace("127.0.0.1","localhost"));
        assertThatThrownBy(()->LedgerRuntimeEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void liveServerMustMatchExactPortDatadirSchemaAndAccountHost(boolean combined) {
        var p=LedgerRuntimeEnvironment.validate(environment(combined));String data=p.datadir()+"/",account=p.user()+"@127.0.0.1";
        assertThatThrownBy(()->LedgerRuntimeEnvironment.connected(p,data,19327,p.database(),account)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->LedgerRuntimeEnvironment.connected(p,data+"other/",p.port(),p.database(),account)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->LedgerRuntimeEnvironment.connected(p,data,p.port(),p.database()+"_r2",account)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->LedgerRuntimeEnvironment.connected(p,data,p.port(),p.database(),p.user()+"@localhost")).isInstanceOf(IllegalStateException.class);
    }
}
