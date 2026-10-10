package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Corrupt and incomplete external material never creates a database release. Originals are restored. */
final class RecoveryReceivedScopeReleaseFiles {
    static void beforeCommit(RecoveryScopeReleaseCoordinator coordinator,RecoveryScopeReleaseStore store,
            HandoverRecoveredPhysicalReview.Request request,Path root) throws Exception {
        Path prepare=root.resolve("scope-release-prepare.json"),pending=root.resolve("scope-release-commit.json.pending");
        changed(prepare,()->assertThatThrownBy(()->coordinator.release(store,request)).isInstanceOf(RecoveryControlException.class));
        Files.writeString(pending,"partial");
        try { assertThatThrownBy(()->coordinator.release(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_CONFLICT"); }
        finally { preserve(pending); }
        Path commit=root.resolve("scope-release-commit.json"); Files.write(commit,store.intent().commitBytes());
        try {
            assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_PROJECTION_MISSING");
            assertThatThrownBy(()->coordinator.release(store,request)).hasMessage("RECOVERY_RELEASE_PROJECTION_MISSING");
        } finally { preserve(commit); }
        Files.move(prepare,root.resolve("scope-release-prepare.json.pending"));
        store.prepare(); assertThat(Files.readAllBytes(prepare)).isEqualTo(store.intent().prepareBytes());
    }
    static void afterCommit(RecoveryScopeReleaseCoordinator coordinator,RecoveryScopeReleaseStore store,
            HandoverRecoveredPhysicalReview.Request request,Path root) throws Exception {
        Path prepare=root.resolve("scope-release-prepare.json"),commit=root.resolve("scope-release-commit.json");
        Path saved=preserve(prepare);
        try { assertThatThrownBy(()->coordinator.release(store,request)).hasMessage("RECOVERY_RELEASE_PREPARE_MISSING"); }
        finally { Files.move(saved,prepare); }
        changed(commit,()->assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_CONFLICT"));
        Path pending=root.resolve("scope-release-commit.json.pending"); Files.write(pending,Files.readAllBytes(commit));
        try { assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_INCOMPLETE"); }
        finally { preserve(pending); }
        Path unknown=root.resolve("unreviewed.json"); Files.writeString(unknown,"unknown");
        try { assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_STORE_UNKNOWN_ARTIFACT"); }
        finally { preserve(unknown); }
        Files.move(commit,pending);
        assertThatThrownBy(()->coordinator.verify(store,request)).hasMessage("RECOVERY_RELEASE_COMMIT_REQUIRED");
        coordinator.release(store,request); assertThat(Files.exists(pending)).isFalse();
    }
    static void identity(RecoveryScopeReleaseCoordinator coordinator,RecoveryScopeReleaseStore store,
            HandoverRecoveredPhysicalReview.Request request,Path root,String lockIdentity) throws Exception {
        assertThatThrownBy(()->new RecoveryScopeReleaseStore(store.approval(),root,"wrong-identity"))
            .hasMessage("RECOVERY_RELEASE_STORE_IDENTITY");
        assertThatThrownBy(()->new RecoveryScopeReleaseStore(store.approval(),root.resolve("."),lockIdentity))
            .isInstanceOf(RecoveryControlException.class);
        Path alternate=Files.createDirectory(root.getParent().resolve("scope-alternate-"+UUID.randomUUID()));
        Files.writeString(alternate.resolve("coordinator.lock"),"0");
        var other=new RecoveryScopeReleaseStore(store.approval(),alternate,RecoveryWriterProtocol.fileIdentity(alternate.resolve("coordinator.lock")));
        assertThatThrownBy(()->coordinator.release(other,request)).hasMessage("RECOVERY_RELEASE_PROJECTION_CONFLICT");
        Path lock=root.resolve("coordinator.lock"),saved=preserve(lock); Files.writeString(lock,"0");
        try { assertThatThrownBy(store::inspect).hasMessage("RECOVERY_RELEASE_STORE_IDENTITY"); }
        finally { preserve(lock); Files.move(saved,lock); }
        assertThat(store.inspect()).isTrue();
    }
    private static void changed(Path file,Runnable test) throws Exception {
        byte[] original=Files.readAllBytes(file); Files.writeString(file,"changed");
        try { test.run(); } finally { preserve(file); Files.write(file,original); }
    }
    static Path preserve(Path file) throws Exception {
        Path saved=file.getParent().getParent().resolve("scope-refusal-"+UUID.randomUUID()+"-"+file.getFileName());
        Files.move(file,saved); return saved;
    }
    private RecoveryReceivedScopeReleaseFiles() { }
}
