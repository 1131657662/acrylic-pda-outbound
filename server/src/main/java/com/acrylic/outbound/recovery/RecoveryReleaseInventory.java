package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Immutable observed scope. It is neither a closure certificate nor a release capability. */
public final class RecoveryReleaseInventory {
    private final ObjectNode facts;
    private final String sha256;
    private final List<String> gaps;

    RecoveryReleaseInventory(ObjectNode facts,List<String> gaps) {
        this.facts=facts.deepCopy(); this.sha256=RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(facts));
        this.gaps=List.copyOf(gaps);
    }

    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return sha256; }
    public List<String> gaps() { return gaps; }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }

    public void requireSame(RecoveryReleaseInventory observed) {
        require(observed!=null && sha256.equals(observed.sha256) && facts.equals(observed.facts)
            && gaps.equals(observed.gaps),"RECOVERY_RELEASE_INVENTORY_CHANGED");
    }
}
