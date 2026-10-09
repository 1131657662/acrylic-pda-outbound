package com.acrylic.outbound.recovery;

import java.util.HashMap;
import java.util.Map;

/** Pinned original bytes, not a business verification, write permit or release decision. */
public final class RecoveryPinnedSource {
    private final RecoverySourceIdentity identity;
    private final RecoveryControlSnapshot control;
    private final String manifestSha256;
    private final Map<String, byte[]> objects;

    RecoveryPinnedSource(RecoverySourceIdentity identity, RecoveryControlSnapshot control, String manifestSha256,
            Map<String, byte[]> objects) {
        this.identity = identity; this.control = control; this.manifestSha256 = manifestSha256;
        var copies = new HashMap<String, byte[]>();
        objects.forEach((key, bytes) -> copies.put(key, bytes.clone()));
        this.objects = Map.copyOf(copies);
    }

    public RecoverySourceIdentity identity() { return identity; }
    public RecoveryControlSnapshot control() { return control; }
    public String manifestSha256() { return manifestSha256; }

    public byte[] original(String kind) {
        byte[] bytes = objects.get(kind);
        if (bytes == null) throw new IllegalArgumentException("RECOVERY_SOURCE_OBJECT_KIND");
        return bytes.clone();
    }

    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
}
