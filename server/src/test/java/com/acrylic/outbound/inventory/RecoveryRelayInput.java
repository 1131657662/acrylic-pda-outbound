package com.acrylic.outbound.inventory;

import com.acrylic.outbound.devices.*;
import com.acrylic.outbound.relay.RelayBundleFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Disposable registered source key; its generated archive has no frozen submission. */
public record RecoveryRelayInput(String username, String user, String warehouse, String viewer, String device, KeyPair key) {
    public static RecoveryRelayInput seed(ConfigurableApplicationContext web) throws Exception {
        var jdbc = web.getBean(JdbcTemplate.class); var passwords = web.getBean(PasswordEncoder.class);
        var supervisor = IdentityFixture.create(jdbc, passwords, "SUPERVISOR");
        var viewer = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
            viewer.user().id().toString(), supervisor.warehouse().toString());
        var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        var key=generator.generateKeyPair(); UUID device=UUID.randomUUID();
        web.getBean(DeviceStore.class).insert(new DeviceStore.NewDevice(device,supervisor.warehouse(),supervisor.user().id()),
            new DeviceCommands.Register("r-"+device,"合成原件来源",Base64.getEncoder().encodeToString(key.getPublic().getEncoded())));
        return new RecoveryRelayInput(supervisor.user().username(),supervisor.user().id().toString(),
            supervisor.warehouse().toString(),viewer.user().username(),device.toString(),key);
    }

    public byte[] archive() throws Exception {
        var order=SubmissionJson.object().put("source","FORMAL").put("orderId",UUID.randomUUID().toString())
            .put("localOrderId",UUID.randomUUID().toString()).put("deviceId",device).put("operatorId",user)
            .put("warehouseId",warehouse).put("timeTrust","UNCONFIRMED_EXPORT_TIME").put("state","IN_PROGRESS");
        return RelayBundleFixture.build(RelayBundleFixture.files(order,null),key);
    }

    public byte[] unverifiedArchive() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        return new RecoveryRelayInput(username,user,warehouse,viewer,device,generator.generateKeyPair()).archive();
    }
}
