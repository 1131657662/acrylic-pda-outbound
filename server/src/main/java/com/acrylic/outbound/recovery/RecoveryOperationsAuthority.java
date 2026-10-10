package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit independently pinned operations identity; never learns trust from an approval envelope. */
public final class RecoveryOperationsAuthority {
    private final RecoveryClosedAuthority closed;
    private final Path identityFile;
    private final String independentSha256;
    private final JsonNode identity;
    private final PublicKey key;

    public RecoveryOperationsAuthority(RecoveryClosedAuthority closed,Path identityFile,String independentSha256) {
        this.closed=Objects.requireNonNull(closed); this.identityFile=Objects.requireNonNull(identityFile);
        require(independentSha256!=null && independentSha256.matches("[a-f0-9]{64}") && !ZERO_HASH.equals(independentSha256),"RECOVERY_OPERATIONS_PIN_REQUIRED");
        this.independentSha256=independentSha256; identity=read(); validate(); key=key(identity);
    }
    void requireUnchanged() { require(identity.equals(read()),"RECOVERY_OPERATIONS_AUTHORITY_CHANGED"); }
    String sha256() { return independentSha256; }
    PublicKey publicKey() { return key; }
    JsonNode identity() { return identity.deepCopy(); }
    RecoveryClosedAuthority closed() { return closed; }

    byte[] material(Path file) {
        try {
            Path checked=external(file);
            require(!checked.equals(external(identityFile)),"RECOVERY_OPERATIONS_MATERIAL_IS_AUTHORITY");
            return bytes(checked);
        } catch(IOException error) { throw new RecoveryControlException("RECOVERY_OPERATIONS_MATERIAL_UNAVAILABLE",error); }
    }
    private JsonNode read() {
        try {
            byte[] bytes=bytes(external(identityFile));
            require(RecoveryControlFormat.sha256(bytes).equals(independentSha256),"RECOVERY_OPERATIONS_PIN_MISMATCH"); return readCanonical(bytes);
        } catch(IOException error) { throw new RecoveryControlException("RECOVERY_OPERATIONS_AUTHORITY_UNAVAILABLE",error); }
    }
    private Path external(Path path) throws IOException {
        require(path.isAbsolute(),"RECOVERY_OPERATIONS_ABSOLUTE_PATH_REQUIRED");
        Path file=RecoveryControlPaths.file(path);
        for(Path root:closed.location().restoreRoots())
            require(!RecoveryControlPaths.overlap(file,root),"RECOVERY_OPERATIONS_INSIDE_RESTORE_SET");
        require(!RecoveryControlPaths.overlap(file,closed.location().root())
            && !RecoveryControlPaths.overlap(file,closed.authorityRoot())
            && !RecoveryControlPaths.overlap(file,closed.checkpoint()),"RECOVERY_OPERATIONS_AUTHORITY_OVERLAP");
        return file;
    }
    private void validate() {
        fields(identity,"formatVersion authorityKind installationId operatorId warehouseIds publicKeyDer"); version(identity);
        require(text(identity,"authorityKind").equals("RECOVERY_OPERATIONS_APPROVAL"),"RECOVERY_OPERATIONS_AUTHORITY_KIND");
        require(uuid(identity,"installationId").equals(closed.control().installationId()),"RECOVERY_OPERATIONS_INSTALLATION");
        uuid(identity,"operatorId"); var warehouses=identity.path("warehouseIds");
        require(warehouses.isArray() && !warehouses.isEmpty() && warehouses.size()<=1000,"RECOVERY_OPERATIONS_WAREHOUSES");
        String prior=null;
        for(var warehouse:warehouses) {
            var wrapper=com.acrylic.outbound.submissions.SubmissionJson.object(); wrapper.set("id",warehouse);
            String value=uuid(wrapper,"id").toString();
            require(prior==null || prior.compareTo(value)<0,"RECOVERY_OPERATIONS_WAREHOUSE_ORDER"); prior=value;
        }
    }
    private static PublicKey key(JsonNode identity) {
        try {
            String encoded=text(identity,"publicKeyDer"); byte[] bytes=Base64.getDecoder().decode(encoded);
            require(bytes.length<=16384 && Base64.getEncoder().encodeToString(bytes).equals(encoded),"RECOVERY_OPERATIONS_KEY_ENCODING");
            var parsed=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
            require(parsed instanceof RSAPublicKey,"RECOVERY_OPERATIONS_KEY_TYPE"); var rsa=(RSAPublicKey)parsed;
            require(rsa.getModulus().bitLength()>=2048 && rsa.getModulus().bitLength()<=8192
                && rsa.getPublicExponent().compareTo(java.math.BigInteger.ONE)>0 && rsa.getPublicExponent().testBit(0)
                && Arrays.equals(bytes,rsa.getEncoded()),"RECOVERY_OPERATIONS_KEY_INVALID"); return parsed;
        } catch(GeneralSecurityException | IllegalArgumentException error) { throw new RecoveryControlException("RECOVERY_OPERATIONS_KEY_INVALID",error); }
    }
    private static byte[] bytes(Path file) throws IOException {
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(RecoveryControlJson.MAX_BYTES+1);
            require(bytes.length>0 && bytes.length<=RecoveryControlJson.MAX_BYTES,"RECOVERY_OPERATIONS_SIZE"); return bytes;
        }
    }
}
