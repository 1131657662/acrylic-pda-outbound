package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Only disposable synthetic P5 inputs; UNKNOWN deliberately invokes production halt(70). */
public final class RecoveryRetirementChildMain {
    public static void main(String[] args) throws Exception {
        Path config = Path.of(args[0]), home = config.getParent();
        var authority = RecoveryRetirementProcess.authority(config);
        try { authority.validateStartup(); }
        catch (Exception refused) { System.out.println(refused.getMessage()); System.exit(2); return; }
        if (args[1].equals("startup")) { authority.retire(); System.out.println("STARTED"); return; }
        var value = RecoveryControlHistory.read(config);
        var beans = new StaticListableBeanFactory(); beans.addBean("authority", authority);
        var channel = new RecoveryAuthorityLeaseChannel(beans.getBeanProvider(RecoveryFenceAuthority.class));
        var registry = new RecoveryOperationRegistry(RecoveryRetirementProcess.location(value),
            Path.of(value.path("checkpoint").asText()), channel);
        var root = registry.admit(new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null));
        Path record = Path.of(value.path("lease").asText()).resolve("leases/" + root.leaseId() + ".json");
        Files.copy(record, home.resolve("published-original.json"));
        if (args[1].equals("delete-failure")) {
            denyDelete(record, root); return;
        }
        root.close();
        Files.writeString(home.resolve("normal-return.txt"), root.phase() + "|roots=" + registry.liveRoots());
        authority.retire();
    }

    private static void denyDelete(Path record, RecoveryOperationLease root) throws Exception {
        // Native read handle permits reads/writes but denies delete sharing: OS refuses rename.
        var handle = com.sun.jna.platform.win32.Kernel32.INSTANCE.CreateFile(record.toString(),
            com.sun.jna.platform.win32.WinNT.GENERIC_READ,
            com.sun.jna.platform.win32.WinNT.FILE_SHARE_READ | com.sun.jna.platform.win32.WinNT.FILE_SHARE_WRITE,
            null, com.sun.jna.platform.win32.WinNT.OPEN_EXISTING, 0, null);
        if (com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE.equals(handle))
            throw new AssertionError("Could not hold native deny-delete handle");
        Path home = Path.of(System.getProperty("java.io.tmpdir")).getParent();
        Files.writeString(home.resolve("deny-delete-handle.txt"), "REAL Windows handle denies FILE_SHARE_DELETE");
        try { root.close(); }
        finally { com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(handle); }
        throw new AssertionError("Native rename refusal did not halt");
    }
}
