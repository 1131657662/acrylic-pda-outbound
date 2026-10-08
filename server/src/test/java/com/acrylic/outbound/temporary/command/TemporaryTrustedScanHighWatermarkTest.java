package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporaryTrustedScanHighWatermarkTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TemporaryTrustedScanHighWatermark high = new TemporaryTrustedScanHighWatermark(jdbc);
    private final UUID set = UUID.randomUUID(), scope = UUID.randomUUID(), submission = UUID.randomUUID();

    @Test void acceptsTheOnlyNextSequenceAfterTrustedHighWatermark() {
        trusted(4L, 4L);

        assertThat(high.requireNext(set, scope, submission, 1, 5)).isEqualTo(4);
    }

    @Test void rejectsClientSequenceThatDoesNotFollowTrustedHighWatermark() {
        trusted(4L, 4L);

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, 6))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_CHANGED"));
    }

    @Test void rejectsMissingTrustedEvidence() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }

    @Test void rejectsNullTrustedWatermark() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new TemporaryTrustedScanHighWatermark.Watermark(null, 4L)));

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }

    @Test void rejectsAmbiguousTrustedEvidenceInsteadOfSelectingOneRow() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new TemporaryTrustedScanHighWatermark.Watermark(4L, 4L),
                new TemporaryTrustedScanHighWatermark.Watermark(4L, 4L)));

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, 5))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }

    @Test void rejectsOverflowInsteadOfWrappingToAValidSequence() {
        trusted(Long.MAX_VALUE, Long.MAX_VALUE);

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, Long.MIN_VALUE))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_SEQUENCE_INVALID"));
    }

    @Test void rejectsTamperedWatermarkAboveTrustedCaptureHighWatermark() {
        trusted(5L, 4L);

        assertThatThrownBy(() -> high.requireNext(set, scope, submission, 1, 6))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }

    private void trusted(Long value, long captureHigh) {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new TemporaryTrustedScanHighWatermark.Watermark(value, captureHigh)));
    }
    @Test void cumulativeHighMayExceedDeviceHighOnlyWithVerifiedInheritedPrefix() {
        watermark(3,1,2);
        assertThat(high.requireNext(set,scope,submission,3,4)).isEqualTo(3);
    }
    @Test void refusesHighBelowImmutablePrefix() {
        watermark(1,3,2);
        assertThatThrownBy(()->high.requireNext(set,scope,submission,3,2)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }
    @Test void refusesSuffixBeyondDeviceHighEvenWithImmutablePrefix() {
        watermark(4,1,2);
        assertThatThrownBy(()->high.requireNext(set,scope,submission,3,5)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_UNTRUSTED"));
    }
    private void watermark(long value,long captureHigh,long prefix) {
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class)))
            .thenReturn(List.of(new TemporaryTrustedScanHighWatermark.Watermark(value,captureHigh,prefix)));
    }
}
