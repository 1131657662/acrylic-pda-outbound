package com.acrylic.outbound.users;

import jakarta.validation.constraints.*;
import java.util.*;

public final class UserCommands {
    private UserCommands() { }
    public record Create(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String username,
        @NotBlank @Size(max=100) String displayName, @NotBlank @Size(min=12,max=72) String password,
        @NotNull @Size(min=1,max=20) List<@NotBlank String> roles,
        @NotNull @Size(min=1,max=50) List<@NotNull UUID> warehouseIds,
        @NotBlank @Size(max=500) String reason) { }
    public record Update(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(max=100) String displayName, @NotNull @Pattern(regexp="ACTIVE|DISABLED") String status,
        @NotNull @Size(min=1,max=20) List<@NotBlank String> roles,
        @NotNull @Size(min=1,max=50) List<@NotNull UUID> warehouseIds,
        @NotBlank @Size(max=500) String reason) { }
    public record Password(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(min=12,max=72) String password, @NotBlank @Size(max=500) String reason) { }
    public record Receipt(String id,String userId,String rowVersion,String status,String action,String createdAt) { }
    public record Role(String code,String name,List<String> permissions,List<String> permissionNames) { }
    public record Warehouse(UUID id,String code,String name) { }
    public record Item(UUID id,String username,String displayName,String status,String rowVersion,
        List<String> roles,List<Warehouse> warehouses) { }
    public record Page(List<Item> items,long total,int page,int pageSize) { }
    public record Query(String username,String status,int page,int pageSize) { }
    public record History(String id,String action,String actorName,String reason,String rowVersion,String createdAt) { }
}
