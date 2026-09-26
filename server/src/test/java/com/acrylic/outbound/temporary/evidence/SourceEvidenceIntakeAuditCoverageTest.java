package com.acrylic.outbound.temporary.evidence;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceEvidenceIntakeAuditCoverageTest {
    @Test void orderedAuditRecordsAndArchiveSegmentsCoverTheSignedPrefix() {
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(
            new SourceEvidenceIntakeAuditCoverage.Range(3, 4),
            new SourceEvidenceIntakeAuditCoverage.Range(1, 1),
            new SourceEvidenceIntakeAuditCoverage.Range(2, 2)), 4)).isTrue();
    }

    @Test void gapsAndOverlapsCannotMasqueradeAsCoverage() {
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(
            new SourceEvidenceIntakeAuditCoverage.Range(1, 1),
            new SourceEvidenceIntakeAuditCoverage.Range(3, 4)), 4)).isFalse();
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(
            new SourceEvidenceIntakeAuditCoverage.Range(1, 2),
            new SourceEvidenceIntakeAuditCoverage.Range(2, 3)), 3)).isFalse();
    }

    @Test void emptyFutureAndOutOfBoundRangesAreRejected() {
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(), 1)).isFalse();
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(
            new SourceEvidenceIntakeAuditCoverage.Range(1, 5)), 4)).isFalse();
        assertThat(SourceEvidenceIntakeAuditCoverage.complete(List.of(
            new SourceEvidenceIntakeAuditCoverage.Range(0, 1)), 1)).isFalse();
    }
}
