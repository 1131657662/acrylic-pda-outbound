package com.acrylic.outbound.devices;

import jakarta.validation.constraints.*;

public final class DeviceCommands {
    private DeviceCommands() { }
    public record Register(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String deviceCode,
        @NotBlank @Size(max=100) String name,@NotBlank @Size(max=200) String publicKey) { }
    public record Status(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Pattern(regexp="ACTIVE|DISABLED") String status,@NotBlank @Size(max=500) String reason) { }
    public record Receipt(String id,String deviceId,String rowVersion,String status) { }
}
