package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit protected store; short file locks never span a closed database transaction. */
public final class RecoveryScopeReleaseStore {
    private static final String PREPARE="scope-release-prepare.json",COMMIT="scope-release-commit.json";
    private final Path root;
    private final String lockIdentity;
    private final RecoveryScopeReleaseIntent intent;
    public RecoveryScopeReleaseStore(RecoveryOperationsApproval approval,Path root,String independentLockIdentity) {
        Objects.requireNonNull(approval); approval.closed().read(); approval.requireMaterials();
        try {
            this.root=RecoveryControlPaths.checked(root); this.lockIdentity=Objects.requireNonNull(independentLockIdentity);
            require(Files.isDirectory(this.root,LinkOption.NOFOLLOW_LINKS),"RECOVERY_RELEASE_STORE_MISSING");
            var closed=approval.closed();
            require(!RecoveryControlPaths.overlap(this.root,closed.location().root())
                && !RecoveryControlPaths.overlap(this.root,closed.authorityRoot())
                && !RecoveryControlPaths.overlap(this.root,closed.checkpoint()),"RECOVERY_RELEASE_STORE_AUTHORITY_OVERLAP");
            for(Path restored:closed.location().restoreRoots())
                require(!RecoveryControlPaths.overlap(this.root,restored),"RECOVERY_RELEASE_STORE_RESTORE_OVERLAP");
            approval.requireOutside(this.root); intent=new RecoveryScopeReleaseIntent(approval,this.root,lockIdentity);
            locked(()->{layout();return null;});
        } catch(IOException error) { throw new RecoveryControlException("RECOVERY_RELEASE_STORE_UNAVAILABLE",error); }
    }
    public void prepare() {
        intent.approval.closed().read(); intent.approval.requireMaterials();
        locked(()->{
            require(Files.exists(root.resolve(PREPARE),LinkOption.NOFOLLOW_LINKS)
                || (!Files.exists(root.resolve(COMMIT),LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(root.resolve(COMMIT+".pending"),LinkOption.NOFOLLOW_LINKS)),"RECOVERY_RELEASE_PREPARE_MISSING");
            InitialNormalDisk.publish(root,PREPARE,intent.prepareBytes()); inspectLocked(); return null;
        });
    }
    public RecoveryOperationsApproval approval() { return intent.approval; }
    RecoveryScopeReleaseIntent intent() { return intent; }
    boolean inspect() { return locked(this::inspectLocked); }
    void complete(byte[] confirmed) {
        require(Arrays.equals(confirmed,intent.commitBytes()),"RECOVERY_RELEASE_COMMIT_CONFLICT");
        locked(()->{inspectLocked();InitialNormalDisk.publish(root,COMMIT,confirmed);inspectLocked();return null;});
    }
    private boolean inspectLocked() throws IOException {
        intent.approval.requireMaterials();
        require(Arrays.equals(InitialNormalDisk.read(root.resolve(PREPARE)),intent.prepareBytes()),"RECOVERY_RELEASE_PREPARE_CONFLICT");
        require(!Files.exists(root.resolve(PREPARE+".pending"),LinkOption.NOFOLLOW_LINKS),"RECOVERY_RELEASE_PREPARE_INCOMPLETE");
        boolean complete=Files.exists(root.resolve(COMMIT),LinkOption.NOFOLLOW_LINKS);
        if(complete) {
            require(!Files.exists(root.resolve(COMMIT+".pending"),LinkOption.NOFOLLOW_LINKS),"RECOVERY_RELEASE_COMMIT_INCOMPLETE");
            require(Arrays.equals(InitialNormalDisk.read(root.resolve(COMMIT)),intent.commitBytes()),"RECOVERY_RELEASE_COMMIT_CONFLICT");
        } else if(Files.exists(root.resolve(COMMIT+".pending"),LinkOption.NOFOLLOW_LINKS)) {
            require(Arrays.equals(InitialNormalDisk.read(root.resolve(COMMIT+".pending")),intent.commitBytes()),"RECOVERY_RELEASE_COMMIT_CONFLICT");
        }
        return complete;
    }
    private <T> T locked(RecoveryControlLock.Read<T> operation) {
        try { return RecoveryControlLock.locked(RecoveryControlLock.STORE_LAYER,root,()->{layout();return operation.run();}); }
        catch(IOException error) { throw new RecoveryControlException("RECOVERY_RELEASE_STORE_UNAVAILABLE",error); }
    }
    private void layout() throws IOException {
        require(root.equals(RecoveryControlPaths.checked(root))
            && lockIdentity.equals(RecoveryWriterProtocol.fileIdentity(RecoveryControlPaths.file(root.resolve("coordinator.lock")))),
            "RECOVERY_RELEASE_STORE_IDENTITY");
        Set<String> names=Set.of("coordinator.lock",PREPARE,COMMIT,PREPARE+".pending",COMMIT+".pending");
        try(var files=Files.newDirectoryStream(root)) {
            for(Path file:files) require(names.contains(file.getFileName().toString()),"RECOVERY_RELEASE_STORE_UNKNOWN_ARTIFACT");
        }
    }
}
