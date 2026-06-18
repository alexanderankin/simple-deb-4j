package deb.simple.build_deb;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.pgpainless.sop.GenerateKeyImpl;
import org.pgpainless.sop.SOPImpl;
import sop.SOP;
import sop.enums.InlineSignAs;

import java.nio.charset.StandardCharsets;

public class DebRepoSigning {
    private static final SOP SOP = new SOPImpl();

    @SneakyThrows
    public SigningKey genKey(String uid) {
        byte[] privateKey = SOP.generateKey()
                .profile(GenerateKeyImpl.RFC4880_RSA4096_PROFILE) // RSA; safest compatibility for apt/gpgv
                .userId(uid)
                .generate()
                .getBytes();

        byte[] publicKey = SOP.extractCert()
                .key(privateKey)
                .getBytes();

        return new SigningKey()
                .setPublicKey(new String(publicKey, StandardCharsets.UTF_8))
                .setPrivateKey(new String(privateKey, StandardCharsets.UTF_8));

    }

    @SneakyThrows
    public SignedRelease signRelease(String release, String gpgPrivateKey) {
        byte[] releaseBytes = (release.endsWith("\n") ? release : release + "\n").getBytes(StandardCharsets.UTF_8);
        return signRelease(releaseBytes, gpgPrivateKey);
    }

    @SneakyThrows
    public SignedRelease signRelease(byte[] releaseBytes, String gpgPrivateKey) {
        byte[] keyBytes = gpgPrivateKey.getBytes(StandardCharsets.UTF_8);

        byte[] inRelease = SOP.inlineSign()
                .mode(InlineSignAs.clearsigned)
                .key(keyBytes)
                .data(releaseBytes)
                .getBytes();

        byte[] releaseGpg = SOP.detachedSign()
                .key(keyBytes)
                .data(releaseBytes)
                .toByteArrayAndResult()
                .getBytes();

        return new SignedRelease()
                .setInRelease(new String(inRelease, StandardCharsets.UTF_8))
                .setReleaseGpg(new String(releaseGpg, StandardCharsets.UTF_8));
    }

    @Data
    @Accessors(chain = true)
    public static class SigningKey {
        String publicKey;
        String privateKey;
    }

    @Data
    @Accessors(chain = true)
    public static class SignedRelease {
        String inRelease;
        String releaseGpg;
    }
}
