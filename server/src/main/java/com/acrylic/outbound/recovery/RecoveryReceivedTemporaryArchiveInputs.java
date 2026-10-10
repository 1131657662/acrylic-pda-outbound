package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original custody/initial-source verification for the received archive consumer, with no target writes. */
final class RecoveryReceivedTemporaryArchiveInputs {
    private final RecoveryClosedAuthority authority;
    private final RecoveryReceiptKeys keys;
    RecoveryReceivedTemporaryArchiveInputs(RecoveryClosedAuthority authority,RecoveryReceiptKeys keys) {
        this.authority=authority; this.keys=keys;
    }

    Original read(RecoveryReceivedTemporaryArchiveConsumer.Request request) {
        var reader=new RecoverySourceReader(authority.location(),authority.checkpoint());
        var source=reader.read(request.sourceRoot(),authority.sourcePinSha256(),authority.sourceIdentity());
        new RecoveryInitialSource(reader,keys).readTemporary(new RecoveryInitialSource.Request(request.sourceRoot(),
            authority.sourcePinSha256(),source.identity(),List.of()),request.evidence());
        var preparation=new RecoveryOriginalTemporaryPreparation(authority.location(),authority.checkpoint(),keys).read(request.preparation(),source);
        var occupancy=new RecoveryOriginalOccupancy(authority.location(),authority.checkpoint(),keys).read(request.occupancy(),source);
        var registration=new RecoveryDeviceRegistration(authority.location(),authority.checkpoint()).read(request.registration());
        require(request.rows().database().equals(request.preparation().database()) && request.rows().database().equals(request.occupancy().database()),
            "RECOVERY_RECEIVED_ARCHIVE_DATABASE");
        RecoveryTemporaryEvidenceTrust.verify(source,registration,request.evidence(),new RecoverySourcePermission(keys).verify(source),
            request.permissions(),request.rows().database());
        var material=RecoveryReceivedTemporaryMaterial.readAtStop(request.rows(),authority,source,request.evidence());
        return new Original(source,material,preparation,occupancy,registration);
    }

    void unchanged(Original expected,RecoveryReceivedTemporaryArchiveConsumer.Request request) {
        var actual=read(request);
        require(actual.source().control().equals(expected.source().control()) && actual.material().equals(expected.material())
            && actual.preparation().equals(expected.preparation()) && actual.occupancy().equals(expected.occupancy())
            && actual.registration().equals(expected.registration()),"RECOVERY_RECEIVED_ARCHIVE_SOURCE_CHANGED");
        for(String kind:RecoverySourceManifest.OBJECTS)
            require(Arrays.equals(actual.source().original(kind),expected.source().original(kind)),"RECOVERY_RECEIVED_ARCHIVE_SOURCE_CHANGED");
    }

    record Original(RecoveryPinnedSource source,JsonNode material,RecoveryOriginalTemporaryPreparation.Proof preparation,
            RecoveryOriginalOccupancy.Proof occupancy,RecoveryDeviceRegistration.Proof registration) { }
}
