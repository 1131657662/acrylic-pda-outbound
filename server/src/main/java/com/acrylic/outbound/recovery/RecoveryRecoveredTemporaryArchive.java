package com.acrylic.outbound.recovery;

import java.util.UUID;

/**
 * Original bytes observed by one successful closed verification transaction. This is a snapshot,
 * never a native server verification, cached capability, supervisor consent or release decision.
 */
public final class RecoveryRecoveredTemporaryArchive {
    private final RecoverySourceIdentity source;
    private final UUID evidenceSetId;
    private final UUID stopObjectId;
    private final Provenance provenance;
    private final byte[] payload;
    private final byte[] terminalReceipt;
    private final byte[] stopProof;

    RecoveryRecoveredTemporaryArchive(RecoverySourceIdentity source, UUID evidenceSetId, UUID stopObjectId,
            Provenance provenance, byte[] payload, byte[] terminalReceipt, byte[] stopProof) {
        this.source=source; this.evidenceSetId=evidenceSetId; this.stopObjectId=stopObjectId;
        this.provenance=provenance; this.payload=payload.clone(); this.terminalReceipt=terminalReceipt.clone();
        this.stopProof=stopProof.clone();
    }

    public RecoverySourceIdentity source() { return source; }
    public UUID evidenceSetId() { return evidenceSetId; }
    public UUID stopObjectId() { return stopObjectId; }
    public Provenance provenance() { return provenance; }
    public byte[] payload() { return payload.clone(); }
    public byte[] terminalReceipt() { return terminalReceipt.clone(); }
    public byte[] stopProof() { return stopProof.clone(); }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }

    public record Provenance(String controlRecordSha256, String sourceManifestSha256, String rowsSha256,
            String occupancySha256, String registrationSha256, String setSha256, String payloadSha256) { }
}
