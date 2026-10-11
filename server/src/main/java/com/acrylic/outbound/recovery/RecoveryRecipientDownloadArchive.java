package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Strict typed reload of the existing retained archive request; no polymorphic input or private key. */
final class RecoveryRecipientDownloadArchive {
    private static final ObjectMapper JSON=mapper();
    static Input read(byte[] bytes) {
        try { return JSON.readValue(bytes,Input.class); }
        catch(IOException | IllegalArgumentException invalid) { throw new RecoveryControlException("RECOVERY_DOWNLOAD_ARCHIVE_INVALID",invalid); }
    }
    static <T> T descriptor(JsonNode value,Class<T> type) {
        try { return JSON.treeToValue(value,type); }
        catch(IOException | IllegalArgumentException invalid) { throw new RecoveryControlException("RECOVERY_DOWNLOAD_DESCRIPTOR_INVALID",invalid); }
    }
    private static ObjectMapper mapper() {
        var module=new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(PublicKey.class,new JsonDeserializer<>() {
            @Override public PublicKey deserialize(JsonParser input,DeserializationContext context) throws IOException {
                JsonNode value=context.readTree(input); fields(value,"algorithm encoded");
                require(text(value,"algorithm").equals("EC"),"RECOVERY_DOWNLOAD_DEVICE_KEY_ALGORITHM");
                byte[] bytes=Base64.getDecoder().decode(text(value,"encoded"));
                require(bytes.length>0 && bytes.length<=16384,"RECOVERY_DOWNLOAD_DEVICE_KEY_SIZE");
                return com.acrylic.outbound.devices.DeviceCrypto.publicKey(text(value,"encoded"));
            }
        });
        return new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(33554432).build()).build())
            .findAndRegisterModules().registerModule(module).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
    record Input(RecoveryControlLocation location,Path checkpoint,RecoveryClosedAuthority.Pins pins,
            RecoveryReceivedTemporaryArchiveConsumer.Request request,JsonNode nativeFacts,long sourcePid) {
        Input {
            require(location!=null && checkpoint!=null && pins!=null && request!=null && nativeFacts!=null && nativeFacts.isObject(),
                "RECOVERY_DOWNLOAD_ARCHIVE_REQUIRED"); nativeFacts=nativeFacts.deepCopy();
        }
        @Override public JsonNode nativeFacts() { return nativeFacts.deepCopy(); }
    }
    private RecoveryRecipientDownloadArchive() { }
}
