package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.exports.ExportModels.*;

class ExportResultSelectionTest {
    @Test void latestSelectionRejectsExplicitVersionSoReplayCannotChangeMeaning() {
        assertThatThrownBy(()->ExportResultSelection.validate(request(new Command(Format.XLSX,"CURRENT_EFFECTIVE",null,1L))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("最新净值");
    }
    @Test void latestSelectionRejectsOriginalScanTemplate() {
        assertThatThrownBy(()->ExportResultSelection.validate(request(new Command(Format.SCAN_XLSX,"CURRENT_EFFECTIVE",null))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("仅支持原确认");
    }
    @Test void originalAndSpecifiedCorrectionKeepTheirSelection() {
        ExportResultSelection.validate(request(new Command(Format.XLSX,"ORIGINAL_CONFIRMED",null)));
        ExportResultSelection.validate(request(new Command(Format.XLSX,"CORRECTED",null,2L)));
        ExportResultSelection.validate(request(new Command(Format.PDF,"CURRENT_EFFECTIVE",null)));
    }
    private static Request request(Command command) {
        return new Request(new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),UUID.randomUUID(),"1",command);
    }
}
