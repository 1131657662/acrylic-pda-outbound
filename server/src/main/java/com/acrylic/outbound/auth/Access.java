package com.acrylic.outbound.auth;

import com.acrylic.outbound.common.BusinessException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Access {
    private final IdentityStore identities;
    public Access(IdentityStore identities) { this.identities = identities; }

    public SessionUser current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SessionUser session))
            throw new BusinessException(401, "UNAUTHENTICATED", "请先登录");
        var user = identities.loadUserByUsername(session.username());
        if (!user.active() || user.authVersion() != session.authVersion())
            throw new BusinessException(401, "SESSION_REVOKED", "账号或授权已变化，请重新登录");
        return user;
    }

    public SessionUser require(String permission, UUID warehouse) {
        SessionUser user = current();
        if (!identities.permissions(user.id()).contains(permission))
            throw new BusinessException(403, "PERMISSION_DENIED", "没有执行此操作的权限");
        if (identities.warehouses(user.id()).stream().noneMatch(w -> w.id().equals(warehouse)))
            throw new BusinessException(404, "WAREHOUSE_NOT_FOUND", "仓库不存在或无权访问");
        return user;
    }
}
