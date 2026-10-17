package com.acrylic.outbound.recovery;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.BackgroundJobExecution;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.mock;

/** Runtime.halt is confined to this disposable independent JVM. */
public final class RecoveryRendererFenceChild {
    public static void main(String[] args) throws Exception {
        Path config = Path.of(args[0]), home = Path.of(args[1]);
        var value = RecoveryControlHistory.read(config);
        var location = new RecoveryControlLocation(Path.of(value.path("root").asText()),
            UUID.fromString(value.path("installation").asText()), List.of(Path.of(value.path("restore").asText())));
        var binding = new RecoveryLeaseRoots.Binding(location, Path.of(value.path("lease").asText()),
            Path.of(value.path("closed").asText()), value.path("identitySha256").asText());
        var authority = new RecoveryFenceAuthority(new RecoveryLeaseRoots(binding, value.path("markerSha256").asText(),
            RecoveryControlFormat.bytes(value.path("anchor").asText())));
        var gate = RecoveryEffectGate.enabled(authority);
        var registry = new RecoveryOperationRegistry(location, Path.of(value.path("checkpoint").asText()), authority::registerOperation);
        try (var root = RecoveryProcessHandoffTest.root(registry, RecoveryOperationKind.WORKER_JOB)) {
            root.bindDispatch(Thread.currentThread());
            try {
                var renderer = RendererDrainReceiptFixture.lostReceipt(home, RecoveryProcessHandoffTest.service(registry, gate));
                renderer.render(mock(BackgroundJobExecution.class), home.resolve("snapshot.json"),
                    home.resolve("attempt/report.xlsx"), ExportModels.Format.XLSX);
                Files.writeString(home.resolve("incorrect-return.txt"), "must halt before returning or publishing");
            } finally { root.unbindAll(); }
        }
        throw new AssertionError("Production unknown sequence did not halt");
    }
}
