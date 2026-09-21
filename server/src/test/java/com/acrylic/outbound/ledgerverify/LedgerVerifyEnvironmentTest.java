package com.acrylic.outbound.ledgerverify;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LedgerVerifyEnvironmentTest {
    private Map<String,String> valid() {
        return new HashMap<>(Map.of("LEDGER_VERIFY","true","OUTBOUND_DB_URL",LedgerVerifyEnvironment.URL,
            "OUTBOUND_DB_USER",LedgerVerifyEnvironment.USER,"OUTBOUND_STORAGE",LedgerVerifyEnvironment.ROOT.resolve("private-files-r2").toString(),
            "OUTBOUND_AUTH_SIGNING_PRIVATE_KEY",LedgerVerifyEnvironment.ROOT.resolve("signing-private-r2.der").toString(),
            "OUTBOUND_AUTH_SIGNING_PUBLIC_KEY",LedgerVerifyEnvironment.ROOT.resolve("signing-public-r2.der").toString()));
    }

    @Test void acceptsOnlyRegisteredDedicatedConfiguration() {
        assertThatCode(() -> LedgerVerifyEnvironment.validate(valid())).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings={"LEDGER_VERIFY","OUTBOUND_DB_URL","OUTBOUND_DB_USER","OUTBOUND_STORAGE",
        "OUTBOUND_AUTH_SIGNING_PRIVATE_KEY","OUTBOUND_AUTH_SIGNING_PUBLIC_KEY"})
    void missingGuardCannotEnableFixture(String key) {
        var env=valid();env.remove(key);
        assertThatThrownBy(() -> LedgerVerifyEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings={"13316","19321","19322","19323","19324","19325","19326"})
    void refusesEveryExistingLabPort(String port) {
        var env=valid();env.put("OUTBOUND_DB_URL",env.get("OUTBOUND_DB_URL").replace("19327",port));
        assertThatThrownBy(() -> LedgerVerifyEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesRetainedFirstAttemptSchema() {
        var env=valid();env.put("OUTBOUND_DB_URL",env.get("OUTBOUND_DB_URL").replace("_20260921_r2","_20260921"));
        assertThatThrownBy(() -> LedgerVerifyEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings={"OUTBOUND_DB_USER","OUTBOUND_STORAGE","OUTBOUND_AUTH_SIGNING_PRIVATE_KEY","OUTBOUND_AUTH_SIGNING_PUBLIC_KEY"})
    void refusesMixedAttemptResources(String key) {
        var env=valid();env.put(key,env.get(key)+"-other");
        assertThatThrownBy(() -> LedgerVerifyEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesAdditionalConnectionParameters() {
        var env=valid();env.put("OUTBOUND_DB_URL",env.get("OUTBOUND_DB_URL")+"&createDatabaseIfNotExist=true");
        assertThatThrownBy(() -> LedgerVerifyEnvironment.validate(env)).isInstanceOf(IllegalStateException.class);
    }
}
