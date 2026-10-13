package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class IndependentCredentialService {
    private final IndependentEnabled enabled;private final IndependentCredentialAccess access;
    private final IndependentCredentialStore store;private final IndependentCredentialTokens tokens;
    public IndependentCredentialService(IndependentEnabled enabled,IndependentCredentialAccess access,
        IndependentCredentialStore store,IndependentCredentialTokens tokens) {
        this.enabled=enabled;this.access=access;this.store=store;this.tokens=tokens;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public IndependentCredentialStore.Receipt issue(Context context,Command command) {
        enabled.require();var actor=access.current();var device=access.lock(actor,context.epoch());
        String digest=com.acrylic.outbound.inventory.ImportFiles.sha256(Base64.getDecoder().decode(device.publicKey()));
        if(!actor.deviceId().equals(command.deviceId()) || !digest.equals(command.deviceKeySha256()))
            throw new BusinessException(409,"INDEPENDENT_DEVICE_BINDING_MISMATCH","原登记设备钥不匹配，原件保留");
        var ticket=store.prepare(actor,context,command);
        if(ticket.replay()!=null)return store.read(ticket.replay(),actor);
        var input=new IndependentCredentialTokens.Input(Ids.next(),actor,context.epoch(),digest,
            Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return store.save(input,tokens.sign(input),ticket,context.requestId());
    }
    @Transactional(readOnly=true)
    public IndependentCredentialStore.Receipt read(UUID id) { enabled.require();return store.read(id,access.current()); }
    public record Command(@NotNull UUID deviceId,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String deviceKeySha256) {
        public static Command read(byte[] bytes) {
            if(bytes.length<1 || bytes.length>4096)throw invalid();
            var value=com.acrylic.outbound.submissions.SubmissionJson.parse(bytes);
            var fields=new HashSet<String>();value.fieldNames().forEachRemaining(fields::add);
            if(!fields.equals(Set.of("deviceId","deviceKeySha256")))throw invalid();
            var id=com.acrylic.outbound.submissions.SubmissionJson.uuid(value,"deviceId");
            String digest=com.acrylic.outbound.submissions.SubmissionJson.text(value,"deviceKeySha256");
            if(!digest.matches("[0-9a-f]{64}"))throw invalid();return new Command(id,digest);
        }
        private static BusinessException invalid() { return new BusinessException(400,"INVALID_INDEPENDENT_CREDENTIAL","独立身份请求格式无效"); }
    }
    public record Context(String epoch,String key,String requestId) { }
}
