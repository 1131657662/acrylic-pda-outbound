package com.acrylic.outbound.pc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class PcExceptionCommands {
    private PcExceptionCommands() { }
    public record Stop(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(max=1000) String reason,boolean physicalConfirmed) { }
    public record Line(@NotNull UUID entryId,@Positive long expectedCycle,
        @NotNull @Pattern(regexp="RETAIN|RELEASE|QUARANTINE") String action,@NotNull @Size(max=1000) String physicalEvidence) { }
    public record Plan(@NotNull UUID caseId,@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Pattern(regexp="[a-f0-9]{64}") String snapshotSha256,@NotBlank @Size(max=100) String targetUsername,
        @NotNull @Pattern(regexp="CONTINUE|CANCEL") String outcome,@NotBlank @Size(max=1000) String reason,
        @NotNull @Size(max=20000) List<@NotNull @Valid Line> lines,boolean physicalConfirmed) { }
    public record Decision(@NotNull UUID planId,@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Pattern(regexp="[a-f0-9]{64}") String payloadSha256,
        @NotNull @Pattern(regexp="APPROVE|REJECT") String decision,@NotBlank @Size(max=1000) String reason,boolean physicalConfirmed) { }
    public record Apply(@NotNull UUID planId,@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Pattern(regexp="[a-f0-9]{64}") String payloadSha256,
        @NotBlank @Size(max=1000) String physicalEvidence,boolean physicalConfirmed) { }
}
