package deb.simple.gpg;

import lombok.Data;
import lombok.experimental.Accessors;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.api.OpenPGPKey;
import org.pgpainless.PGPainless;
import org.pgpainless.key.generation.type.rsa.RsaLength;

public class GenerateGpgKey {
    public GpgKey genGpg(String name, String email) {
        return genGpg(name, email, RsaLength._4096);
    }

    public GpgKey genGpg(String name, String email, RsaLength rsaLength) {
        OpenPGPKey secretKeys = PGPainless.generateKeyRing()
                .simpleRsaKeyRing(name + " <"+email+">", rsaLength);

        var privateKey = PGPainless.asciiArmor(secretKeys);
        var publicKey = PGPainless.asciiArmor(secretKeys.toCertificate());
        return new GpgKey().setPublicKey(publicKey).setPrivateKey(privateKey);
    }

    @Data
    @Accessors(chain = true)
    public static class GpgKey {
        String publicKey;
        String privateKey;
    }
}
