package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Invalid retained trust inputs must fail before even inspecting a target connection. */
class RecoveryTemporaryPreparationRestoreTest {
    @Test void missingSourcePinCannotOpenTargetDatabase() throws Exception {
        var f=new RecoveryTemporaryRestoreFixture(); var database=mock(DataSource.class);
        var request=new RecoveryTemporaryPreparationRestore.Request(f.request.materialRoot(),null,f.request.original());
        assertThatThrownBy(() -> new RecoveryTemporaryPreparationRestore(database,f.supply.authority(),f.keys).restore(request))
            .hasMessage("RECOVERY_SOURCE_PIN_REQUIRED"); verifyNoInteractions(database);
    }

    @Test void missingIndependentOriginalObservationPinCannotOpenTargetDatabase() throws Exception {
        var f=new RecoveryTemporaryRestoreFixture(); var database=mock(DataSource.class); var old=f.request.original();
        var missing=new RecoveryOriginalTemporaryPreparation.Request(old.materialRoot(),null,old.database(),old.notAfter());
        var request=new RecoveryTemporaryPreparationRestore.Request(f.request.materialRoot(),f.request.sourcePinSha256(),missing);
        assertThatThrownBy(() -> new RecoveryTemporaryPreparationRestore(database,f.supply.authority(),f.keys).restore(request))
            .hasMessage("RECOVERY_TEMP_ORIGINAL_PIN"); verifyNoInteractions(database);
    }

    @Test void changedOriginalObservationCannotOpenTargetDatabase() throws Exception {
        var f=new RecoveryTemporaryRestoreFixture(); var database=mock(DataSource.class);
        Files.writeString(f.source.materials.resolve(f.request.original().independentSha256()+".json"),"{}\n");
        assertThatThrownBy(() -> new RecoveryTemporaryPreparationRestore(database,f.supply.authority(),f.keys).restore(f.request))
            .hasMessage("RECOVERY_TEMP_ORIGINAL_PIN_MISMATCH"); verifyNoInteractions(database);
    }

    @Test void changedNativePartCannotOpenTargetDatabase() throws Exception {
        var f=new RecoveryTemporaryRestoreFixture(); var database=mock(DataSource.class);
        Files.writeString(f.source.object("origin"),"different-original-bytes\n");
        assertThatThrownBy(() -> new RecoveryTemporaryPreparationRestore(database,f.supply.authority(),f.keys).restore(f.request))
            .hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH"); verifyNoInteractions(database);
    }

    @Test void staleControlCannotOpenTargetDatabase() throws Exception {
        var f=new RecoveryTemporaryRestoreFixture(); var database=mock(DataSource.class); var authority=f.supply.authority();
        f.source.control.append(f.source.control.next("VERIFYING"));
        assertThatThrownBy(() -> new RecoveryTemporaryPreparationRestore(database,authority,f.keys).restore(f.request))
            .hasMessage("CONTROL_CLOSED_HEAD_MISMATCH"); verifyNoInteractions(database);
    }
}
