package deb.simple.gpg;

import org.junit.jupiter.api.Test;
import org.pgpainless.key.generation.type.rsa.RsaLength;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateGpgKeyTest {

    @Test
    void test() {
        var example = new GenerateGpgKey().genGpg("Example", "info@example.com", RsaLength._4096);
        assertTrue(example.getPrivateKey().contains("-----BEGIN PGP PRIVATE KEY BLOCK-----"));
        assertTrue(example.getPublicKey().contains("-----BEGIN PGP PUBLIC KEY BLOCK-----"));
    }
}
