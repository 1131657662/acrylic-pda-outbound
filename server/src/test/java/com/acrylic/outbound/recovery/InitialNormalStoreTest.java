package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class InitialNormalStoreTest {
    @TempDir Path directory;

    @Test void durablePrepareAndCompletionSurviveNewReaderWithoutChangingOriginal() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        fixture.store.prepare(fixture.original);
        assertThat(fixture.store.inspect().committed()).isFalse();
        fixture.complete();
        fixture.store.prepare(fixture.original); fixture.complete();
        var reopened = new InitialNormalStore(new RecoveryControlLocation(fixture.root,
            InitialNormalFixture.INSTALLATION, List.of(fixture.restore)), fixture.trusted);
        assertThat(reopened.inspect().committed()).isTrue();
        assertThat(reopened.inspect().request().originalBytes()).isEqualTo(fixture.original);
        assertThat(Files.readAllBytes(fixture.trusted)).isEqualTo(fixture.original);
    }

    @Test void sameCommandWithDifferentBodyCannotOverwrite() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original);
        byte[] conflicting = RecoveryControlJson.encode(InitialNormalFixture.request().put("operatorId", "different"));
        assertThatThrownBy(() -> fixture.store.prepare(conflicting)).hasMessage("INITIAL_NORMAL_REQUEST_CONFLICT");
        assertThat(Files.readAllBytes(fixture.root.resolve(InitialNormalStore.REQUEST))).isEqualTo(fixture.original);
    }

    @Test void incompletePublishCanResumeOnlyWithExactOriginal() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        Files.write(fixture.root.resolve(InitialNormalStore.REQUEST + ".pending"), fixture.original);
        assertThatThrownBy(fixture.store::inspect).isInstanceOf(RecoveryControlException.class);
        fixture.store.prepare(fixture.original);
        assertThat(fixture.store.inspect().committed()).isFalse();
    }

    @Test void tornPublishRemainsPreservedAndDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        Path pending = fixture.root.resolve(InitialNormalStore.REQUEST + ".pending");
        Files.writeString(pending, "torn");
        assertThatThrownBy(() -> fixture.store.prepare(fixture.original)).hasMessage("INITIAL_NORMAL_FILE_CONFLICT");
        assertThat(Files.readString(pending)).isEqualTo("torn");
        assertThat(fixture.root.resolve(InitialNormalStore.REQUEST)).doesNotExist();
    }

    @Test void completionRequiresMatchingDatabaseEvidence() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original);
        assertThatThrownBy(() -> fixture.store.locked(() -> {
            fixture.store.completeLocked("b".repeat(64)); return null;
        })).hasMessage("INITIAL_NORMAL_COMPLETION_CONFLICT");
        assertThat(fixture.store.inspect().committed()).isFalse();
    }

    @Test void changedOrMissingTrustedOriginalNeverSelfBootstraps() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        Files.write(fixture.trusted, RecoveryControlJson.encode(InitialNormalFixture.request().put("operatorId", "changed")));
        assertThatThrownBy(fixture.store::inspect).hasMessage("INITIAL_NORMAL_REQUEST_CONFLICT");
        Files.move(fixture.trusted, directory.resolve("saved-trusted-original.json"));
        assertThatThrownBy(fixture.store::inspect).isInstanceOf(RecoveryControlException.class);
    }

    @Test void tamperedCompletionIsDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory); fixture.store.prepare(fixture.original); fixture.complete();
        Files.write(fixture.root.resolve(InitialNormalStore.COMPLETE),
            new InitialNormalCompletion("b".repeat(64), "b".repeat(64)).bytes());
        assertThatThrownBy(fixture.store::inspect).hasMessage("INITIAL_NORMAL_COMPLETION_CONFLICT");
    }

    @Test void trustedOriginalInsideRestoreSetIsDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        Path wrong = Files.write(fixture.restore.resolve("trusted.json"), fixture.original);
        var store = new InitialNormalStore(new RecoveryControlLocation(fixture.root,
            InitialNormalFixture.INSTALLATION, List.of(fixture.restore)), wrong);
        assertThatThrownBy(() -> store.prepare(fixture.original)).hasMessage("CONTROL_CHECKPOINT_INSIDE_RESTORE_SET");
    }

    @Test void fileAliasesAreDenied() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        Files.createLink(directory.resolve("alias.json"), fixture.trusted);
        assertThatThrownBy(() -> fixture.store.prepare(fixture.original)).hasMessage("CONTROL_FILE_ALIAS");
    }

    @Test void secondCoordinatorCannotEnter() throws Exception {
        var fixture = new InitialNormalFixture(directory);
        RecoveryControlLock.locked(fixture.root, () -> {
            assertThatThrownBy(() -> fixture.store.prepare(fixture.original)).hasMessage("CONTROL_COORDINATOR_BUSY");
            return null;
        });
    }
}
