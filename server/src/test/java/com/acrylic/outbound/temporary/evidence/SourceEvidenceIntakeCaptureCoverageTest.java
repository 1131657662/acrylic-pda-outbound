package com.acrylic.outbound.temporary.evidence;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceEvidenceIntakeCaptureCoverageTest {
    @Test void acceptsAnExactCapturePrefix() {
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(1L, 2L, 3L), 3)).isTrue();
    }

    @Test void rejectsASequenceGap() {
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(1L, 3L), 3)).isFalse();
    }

    @Test void rejectsDuplicatesAndUnexpectedRecords() {
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(1L, 1L), 2)).isFalse();
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(1L), 0)).isFalse();
    }

    @Test void acceptsAnEmptyPrefixOnlyAtZero() {
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(), 0)).isTrue();
        assertThat(SourceEvidenceIntakeCaptureCoverage.complete(List.of(), 1)).isFalse();
    }
}
