package com.acrylic.outbound.reports;

import java.util.Locale;
import java.util.UUID;

/** 扫码人姓名可用性：只排除缺失和占位值（原人员ID前缀、人员ID本身、账号），不要求汉字，中英文姓名均可。 */
public final class OperatorNames {
    private OperatorNames() { }

    public static boolean usable(String name, String... reserved) {
        if (name == null) return false;
        String candidate = name.strip();
        if (candidate.isEmpty() || candidate.startsWith("原人员ID:")) return false;
        if (isUuid(candidate) || isMachineId(candidate)) return false;
        for (String value : reserved) {
            if (value == null) continue;
            String other = value.strip();
            if (other.isEmpty()) continue;
            if (candidate.equalsIgnoreCase(other) || compact(candidate).equals(compact(other))) return false;
        }
        return true;
    }

    private static boolean isUuid(String candidate) {
        if (candidate.length() != 36) return false;
        try { UUID.fromString(candidate); return true; } catch (IllegalArgumentException notUuid) { return false; }
    }

    private static boolean isMachineId(String candidate) {
        if (candidate.length() != 32) return false;
        for (int i = 0; i < candidate.length(); i++) if (Character.digit(candidate.charAt(i), 16) < 0) return false;
        return true;
    }

    private static String compact(String value) {
        return value.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
