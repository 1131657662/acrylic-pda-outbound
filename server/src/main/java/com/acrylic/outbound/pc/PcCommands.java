package com.acrylic.outbound.pc;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class PcCommands {
    private PcCommands() { }
    public record Start(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull UUID writerId,@NotNull @Size(max=18) String policyVersion,@NotNull @Size(max=500) String reason) { }
    public record Write(@NotNull UUID writerId,@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String fence,
        @NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Size(max=11) String barcode,UUID entryId,@NotNull @Size(max=500) String reason,boolean physicalConfirmed) { }
    public record Review(@NotNull @Pattern(regexp="[a-f0-9]{64}") String payloadSha256,
        @NotNull @Pattern(regexp="APPROVE|REJECT") String decision,@NotBlank @Size(max=500) String reason) { }
    public record Resume(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(max=1000) String physicalEvidence) { }
}
