package com.acrylic.outbound.common;

import java.util.List;
import java.util.function.Function;

public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {
    public static <T> Page<T> from(List<T> rows, int limit, Function<T, String> cursor) {
        boolean more = rows.size() > limit;
        List<T> items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        return new Page<>(items, more ? cursor.apply(items.getLast()) : null, more);
    }
    public static int checkedLimit(int limit) {
        if (limit < 1 || limit > 200) throw new BusinessException(400, "INVALID_PAGE_SIZE", "每页必须为1至200条");
        return limit;
    }
}
