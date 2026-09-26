package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class E2aV34EvidenceBindingTest {
    @ParameterizedTest
    @ValueSource(strings = {"mapping0", "mapped", "abort"})
    void bindsEverySyntheticEvidenceStatement(String mode, @TempDir Path storage) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object[] parameters = java.util.Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length);
            assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(parameters.length);
            return 1;
        });
        var result = new HashMap<String, String>();

        E2aV34Seed.seedEvidence(jdbc, storage, result, mode,
            "warehouse", "scope", "submission", "device", "operator");

        assertThat(result).containsKeys(mode + "SetId", mode + "ObjectId", mode + "VerificationId");
    }
}
