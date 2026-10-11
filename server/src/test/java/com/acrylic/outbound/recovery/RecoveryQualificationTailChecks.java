package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverRecoveredQualificationTailChecks;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual late identity, original-row, and pinned-material changes, with transaction rollback. */
final class RecoveryQualificationTailChecks {
    static void verify(RecoveryQualificationFixture f,RecoveryTemporaryQualification qualification) throws Exception {
        var e=f.enrollment;
        reject(f,qualification,()->assertThat(e.jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-qualification-tail') WHERE id<>?",
            Ids.bytes(uuid(f.body,"warehouseId")))).isPositive());
        reject(f,qualification,()->assertThat(e.jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",
            Ids.bytes(uuid(f.body.path("recipient"),"userId")))).isEqualTo(1));
        var signed=f.sign(f.body); var material=RecoveryTemporaryQualification.verify(signed,f.admission);
        byte[] original=Files.readAllBytes(signed.file());
        try {
            reject(f,material,()->{
                try { Files.writeString(signed.file(),"tampered qualification"); }
                catch(java.io.IOException error) { throw new UncheckedIOException(error); }
            });
        } finally { Files.write(signed.file(),original); }
    }
    private static void reject(RecoveryQualificationFixture f,RecoveryTemporaryQualification qualification,Runnable change) {
        var e=f.enrollment;
        HandoverRecoveredQualificationTailChecks.reject(e.app,new HandoverRecoveredQualificationTailChecks.Input(
            qualification,e.setup.request(),e.setup.registrar(),change));
    }
    private RecoveryQualificationTailChecks() { }
}
