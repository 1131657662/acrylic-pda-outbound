package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class OrderCommands {
    private OrderCommands() { }
    public record Line(@NotNull @Pattern(regexp="[A-Z0-9]{3}") String deliveryCode,
                       @NotNull @Positive Integer plannedCount) { }
    public record Plan(@NotBlank @Size(max=40) String vehicleNo, @NotNull @Size(max=40) String containerNo,
        @NotNull @Size(max=1000) String notes, @NotNull UUID expectedActiveSnapshot,
        @NotNull @Pattern(regexp="[0-9]{1,18}") String expectedMappingRevision,
        @NotEmpty @Size(max=1000) List<@NotNull @Valid Line> lines) { }
    public record Revision(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(max=500) String reason, @NotNull @Valid Plan plan,
        @Pattern(regexp="[1-9][0-9]{0,17}") String expectedPolicyVersion) { }
    public record Cancellation(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotBlank @Size(max=500) String reason) { }

    public static long total(List<Line> lines) {
        if (lines.isEmpty() || lines.stream().map(Line::deliveryCode).distinct().count() != lines.size())
            throw new BusinessException(400, "ORDER_DUPLICATE_PRODUCT", "订单至少有一个产品，发货代码不能重复");
        return lines.stream().mapToLong(line -> {
            if (line.plannedCount() == null || line.plannedCount() <= 0)
                throw new BusinessException(400, "ORDER_INVALID_PLAN", "每个产品的计划包数必须为正整数");
            return line.plannedCount().longValue();
        }).reduce(0L, Math::addExact);
    }
}
