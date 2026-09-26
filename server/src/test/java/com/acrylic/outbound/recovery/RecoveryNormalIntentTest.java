package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The durable external NORMAL intent must be exact: a partial or forged artifact never admits NORMAL. */
class RecoveryNormalIntentTest {
    private static final String SCHEMA = "acrylic_e2a_cross";

    @Test void absentIntentIsReportedAsAbsentRatherThanGuessed() throws Exception {
        var fixture = protocol();

        assertThat(load(fixture).intent()).isNull();
    }

    @Test void validIntentBindsTheClosedChainAndIsReturnedExactly() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        draft.write();
        var head = fixture.read();

        var intent = load(fixture).intent();

        assertThat(intent).isNotNull();
        assertThat(intent.installation()).isEqualTo(fixture.installation);
        assertThat(intent.schema()).isEqualTo(SCHEMA);
        assertThat(intent.baseline()).isEqualTo(1);
        assertThat(intent.revision()).isEqualTo(Long.parseLong(head.revision()));
        assertThat(intent.recordSha256()).isEqualTo(head.recordSha256());
        assertThat(intent.previousRecordSha256()).isEqualTo(head.previousRecordSha256());
        assertThat(intent.operatorId()).isEqualTo("operator-1");
        assertThat(fixture.read().ordinaryWritesAllowed()).isFalse();
    }

    @Test void intentMustDeclareItsOwnInstallation() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        draft.body().put("installationId", UUID.randomUUID().toString());
        draft.write();

        reject(fixture, "CONTROL_NORMAL_INTENT_INSTALLATION");
    }

    @Test void intentMustMatchTheControlRecordItClaims() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        draft.body().put("controlRecordSha256", "b".repeat(64));
        draft.write();

        var intent = load(fixture).intent();
        assertThat(intent).isNotNull();
        assertThatThrownBy(() -> RecoveryNormalIntent.requireBound(intent, fixture.read()))
            .isInstanceOf(RecoveryControlException.class).hasMessage("CONTROL_NORMAL_INTENT_CONTROL_MISMATCH");
    }

    @Test void intentMustBeHashBoundToItsOwnContent() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        draft.write("a".repeat(64));

        reject(fixture, "CONTROL_NORMAL_INTENT_HASH");
    }

    @Test void intentRejectsAStaleChainContinuity() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        draft.body().put("previousRecordSha256", RecoveryControlFormat.ZERO_HASH);
        draft.write();

        var intent = load(fixture).intent();
        assertThat(intent).isNotNull();
        assertThatThrownBy(() -> RecoveryNormalIntent.requireBound(intent, fixture.read()))
            .isInstanceOf(RecoveryControlException.class).hasMessage("CONTROL_NORMAL_INTENT_CONTROL_MISMATCH");
    }

    @Test void intentRequiresASchemaBaselineOperatorAndRevision() throws Exception {
        var blankSchema = protocol();
        var blankSchemaDraft = RecoveryControlFixture.normal(blankSchema, SCHEMA);
        blankSchemaDraft.body().put("expectedDatabaseSchema", " ");
        blankSchemaDraft.write();
        reject(blankSchema, "CONTROL_NORMAL_INTENT_SCHEMA");

        var zeroBaseline = protocol();
        var zeroBaselineDraft = RecoveryControlFixture.normal(zeroBaseline, SCHEMA);
        zeroBaselineDraft.body().put("expectedBaselineNumericEpoch", 0);
        zeroBaselineDraft.write();
        reject(zeroBaseline, "CONTROL_NORMAL_INTENT_BASELINE");

        var zeroRevision = protocol();
        var zeroRevisionDraft = RecoveryControlFixture.normal(zeroRevision, SCHEMA);
        zeroRevisionDraft.body().put("controlRevision", 0);
        zeroRevisionDraft.write();
        reject(zeroRevision, "CONTROL_NORMAL_INTENT_REVISION");

        var blankOperator = protocol();
        var blankOperatorDraft = RecoveryControlFixture.normal(blankOperator, SCHEMA);
        blankOperatorDraft.body().put("operatorId", "");
        blankOperatorDraft.write();
        reject(blankOperator, "CONTROL_NORMAL_INTENT_OPERATOR");
    }

    @Test void intentRejectsUnknownFieldsInsteadOfIgnoringThem() throws Exception {
        var fixture = protocol();
        var draft = RecoveryControlFixture.normal(fixture, SCHEMA);
        var body = draft.body().deepCopy();
        body.put("releaseReady", true);
        var withExtra = body;
        withExtra.put("intentSha256", RecoveryControlFormat.hash(body));
        RecoveryControlFixture.write(fixture.root.resolve(RecoveryNormalIntent.FILE), withExtra);

        reject(fixture, "CONTROL_FIELDS");
    }

    @Test void readerAcceptsTheNormalIntentArtifactButStillRejectsUnknownNames() throws Exception {
        var fixture = protocol();
        RecoveryControlFixture.normal(fixture, SCHEMA).write();

        assertThat(fixture.read().revision()).isEqualTo("4");

        Files.writeString(fixture.root.resolve("authority.tmp"), "partial");
        assertThatThrownBy(fixture::read).isInstanceOf(RecoveryControlException.class)
            .hasMessage("CONTROL_UNKNOWN_ARTIFACT");
    }

    private static RecoveryNormalIntent.Requirement load(RecoveryControlFixture fixture) throws Exception {
        return RecoveryNormalIntent.load(fixture.location(), fixture.root, 1);
    }

    private static void reject(RecoveryControlFixture fixture, String code) {
        assertThatThrownBy(() -> load(fixture)).isInstanceOf(RecoveryControlException.class).hasMessage(code);
    }

    /** Closed-phase chain at RELEASE_READY: the only state from which initial NORMAL may be registered. */
    static RecoveryControlFixture protocol() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        fixture.toReleaseReady();
        return fixture;
    }
}
