package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record SourceEvidenceIdentity(@NotNull UUID warehouseId,@NotNull UUID creatorDeviceId,
    @NotNull UUID clientOrderId,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String workRound) {
    long round() {
        try { return Long.parseLong(workRound); }
        catch(NumberFormatException error) { throw new BusinessException(400,"INVALID_WORK_ROUND","原提交轮次格式无效"); }
    }
    record Selection(UUID submissionId,SourceEvidenceIdentity identity) { }
}
