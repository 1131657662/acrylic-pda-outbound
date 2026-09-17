import java.nio.file.*;
import java.security.*;

// Only for the isolated local development environment. Production provisions keys separately.
class CreateDevelopmentSigningKey {
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]).toAbsolutePath().normalize();
        Path privateFile=directory.resolve("private.pk8"),publicFile=directory.resolve("public.der");
        if(Files.exists(privateFile) && Files.exists(publicFile)) return;
        if(Files.exists(privateFile) || Files.exists(publicFile)) throw new IllegalStateException("Incomplete signing key; refusing replacement");
        Files.createDirectories(directory);
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);var pair=generator.generateKeyPair();
        Files.write(privateFile,pair.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
        Files.write(publicFile,pair.getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);
        System.out.println("Created local development signing key. Keep private.pk8 private and backed up.");
    }
}
