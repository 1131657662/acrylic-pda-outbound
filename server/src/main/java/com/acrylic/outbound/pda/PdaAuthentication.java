package com.acrylic.outbound.pda;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PdaAuthentication {
    private final IdentityStore identities;private final PasswordEncoder passwords;private final PdaAuthLocks locks;
    private final PdaChallenges challenges;private final PdaSessions sessions;private final AuditWriter audit;
    private final String dummyHash;
    public PdaAuthentication(IdentityStore identities,PasswordEncoder passwords,PdaAuthLocks locks,PdaChallenges challenges,PdaSessions sessions,AuditWriter audit) {
        this.identities=identities;this.passwords=passwords;this.locks=locks;this.challenges=challenges;this.sessions=sessions;this.audit=audit;
        this.dummyHash=passwords.encode(DeviceCrypto.secret());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PdaSessions.Tokens login(Login input,String requestId) {
        var device=locks.device(input.deviceId());SessionUser user=null;
        try { user=identities.loadUserByUsername(input.username()); } catch(org.springframework.security.core.userdetails.UsernameNotFoundException ignored) { }
        boolean matches=passwords.matches(input.password(),user==null?dummyHash:user.passwordHash());
        if(user==null || !matches || !user.active() || !identities.permissions(user.id()).contains("pda:login") ||
            identities.warehouses(user.id()).stream().noneMatch(w->w.id().equals(device.warehouseId())))
            throw new BusinessException(401,"INVALID_CREDENTIALS","账号、密码或设备授权无效");
        challenges.consume(new PdaChallenges.Proof(input.challengeId(),"LOGIN",input.signature()),device,input.username());
        var tokens=sessions.issue(user,device);
        audit.append(new AuditWriter.Event(user.id(),"PDA_LOGIN","DEVICE",device.id(),"{}"),requestId);
        return tokens;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PdaSessions.Tokens refresh(Refresh input,String requestId) {
        var device=locks.device(input.deviceId());var principal=sessions.refresh(input.refreshToken(),device);
        challenges.consume(new PdaChallenges.Proof(input.challengeId(),"REFRESH",input.signature()),device,input.refreshToken());
        var result=sessions.rotate(principal,device);
        audit.append(new AuditWriter.Event(principal.userId(),"PDA_TOKEN_ROTATED","DEVICE",device.id(),"{}"),requestId);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void logout(PdaSessions.Principal principal,String requestId) {
        locks.device(principal.deviceId());sessions.revoke(principal.sessionId());
        audit.append(new AuditWriter.Event(principal.userId(),"PDA_LOGOUT","DEVICE",principal.deviceId(),"{}"),requestId);
    }
    public record Login(@NotNull @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String username,@NotBlank @Size(max=72) String password,
        @NotNull UUID deviceId,@NotNull UUID challengeId,@NotBlank @Size(max=120) String signature) { }
    public record Refresh(@NotNull UUID deviceId,@NotNull UUID challengeId,@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String refreshToken,
        @NotBlank @Size(max=120) String signature) { }
}
