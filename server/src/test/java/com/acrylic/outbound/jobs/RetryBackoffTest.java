package com.acrylic.outbound.jobs;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetryBackoffTest {
    @ParameterizedTest
    @CsvSource({"1,5", "2,10", "3,20", "8,640", "9,900", "20,900", "2147483647,900"})
    void exponentiallyBacksOffWithoutOverflow(int attempt, long expectedSeconds) {
        assertThat(RetryBackoff.forAttempt(attempt).toSeconds()).isEqualTo(expectedSeconds);
    }

    @Test void rejectsAttemptsThatHaveNotStarted() {
        assertThatThrownBy(() -> RetryBackoff.forAttempt(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
