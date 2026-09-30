import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Generates the isolated harness signing keypair in exactly the encodings the application expects:
 * PKCS#8 for the private key and X.509/SPKI for the public key (both DER), the same bytes that
 * {@code E2aCrossSourceHttpSetup.signing} writes via {@code getEncoded()}.
 *
 * <p>Usage: {@code java GenSigningKey.java <private.der> <public.der>}
 */
public final class GenSigningKey {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: GenSigningKey <private.der> <public.der>");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Files.write(Path.of(args[0]), pair.getPrivate().getEncoded());
        Files.write(Path.of(args[1]), pair.getPublic().getEncoded());
        System.out.println("GenSigningKey private=" + pair.getPrivate().getEncoded().length
            + " public=" + pair.getPublic().getEncoded().length);
    }
}
