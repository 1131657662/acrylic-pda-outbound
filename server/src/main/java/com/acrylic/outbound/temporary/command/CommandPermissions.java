package com.acrylic.outbound.temporary.command;

import java.util.*;

final class CommandPermissions {
    private static final Map<String,String> KNOWN=Map.of("NORMAL_CONFIRM","temporary_order:confirm",
        "SHORT_APPROVE","submission:review","LINK","temporary_order:link",
        "REJECT","submission:review",
        "PHYSICAL_DISPOSITION","order:dispose","CLOSE_DISPOSITION","order:dispose","REOPEN_WORK","order:resume");
    private CommandPermissions() { }
    static String execution(String kind) { return KNOWN.get(kind); }
    static boolean mayRead(Collection<String> permissions,String kind) {
        return permissions.contains("temporary_order:confirm") || KNOWN.containsKey(kind) && permissions.contains(KNOWN.get(kind));
    }
    static boolean mayEnter(Collection<String> permissions) {
        return permissions.contains("temporary_order:confirm") || KNOWN.values().stream().anyMatch(permissions::contains);
    }
    static void requireWrite(Collection<String> permissions,String kind) {
        String permission=execution(kind);
        if(permission==null)throw CommandErrors.unsupported();
        if(!permissions.contains(permission))throw new com.acrylic.outbound.common.BusinessException(403,"PERMISSION_DENIED","没有执行此命令的权限");
    }
}
