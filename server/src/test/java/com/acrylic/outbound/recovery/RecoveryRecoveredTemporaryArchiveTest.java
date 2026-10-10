package com.acrylic.outbound.recovery;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryRecoveredTemporaryArchiveTest {
    @Test void originalBytesCannotBeChangedThroughInputsOrReturnedArrays() {
        byte[] payload={1},receipt={2},stop={3};
        var archive=new RecoveryRecoveredTemporaryArchive(null,UUID.randomUUID(),UUID.randomUUID(),null,payload,receipt,stop);
        payload[0]=4; receipt[0]=5; stop[0]=6;
        assertThat(archive.payload()).containsExactly((byte)1);
        assertThat(archive.terminalReceipt()).containsExactly((byte)2);
        assertThat(archive.stopProof()).containsExactly((byte)3);
        archive.payload()[0]=7; archive.terminalReceipt()[0]=8; archive.stopProof()[0]=9;
        assertThat(archive.payload()).containsExactly((byte)1);
        assertThat(archive.terminalReceipt()).containsExactly((byte)2);
        assertThat(archive.stopProof()).containsExactly((byte)3);
    }

    @Test void callersCannotConstructAnArchiveOrUseItAsOrdinaryAdmission() {
        assertThat(RecoveryRecoveredTemporaryArchive.class.getConstructors()).isEmpty();
        var archive=new RecoveryRecoveredTemporaryArchive(null,UUID.randomUUID(),UUID.randomUUID(),null,new byte[]{1},new byte[]{2},new byte[]{3});
        assertThat(archive.ordinaryWritesAllowed()).isFalse();
        assertThat(archive.localDeletionAllowed()).isFalse();
    }
}
