package deb.simple.build_deb;

import lombok.SneakyThrows;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

public class BuildRepositoryGpgSignatures {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static PGPPublicKey readPublicKey(String armoredKeyBlock) throws IOException, PGPException {
        InputStream in = new ByteArrayInputStream(armoredKeyBlock.getBytes());
        in = PGPUtil.getDecoderStream(in);  // optional if already ArmoredInputStream
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(in, new JcaKeyFingerprintCalculator());

        for (PGPPublicKeyRing keyRing : pgpPub) {
            for (PGPPublicKey key : keyRing) {
                if (key.isEncryptionKey() || key.isMasterKey()) {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException("No suitable public key found.");
    }

    @SneakyThrows
    public static Map<String, String> signReleaseFile(String armoredPrivateKey, String armoredPublicKey, byte[] releaseContents) {
        Map<String, String> result = new HashMap<>();

        // Step 1: Load private key
        PGPSecretKey secretKey = readSigningKey(armoredPrivateKey);
        PGPPrivateKey privateKey = extractPrivateKey(secretKey);  // null passphrase

        result.put("repository.gpg", armoredPublicKey);
        PGPPublicKey publicKey = readPublicKey(armoredPublicKey);


        ByteArrayOutputStream clearOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream clearArmored = ArmoredOutputStream.builder().clearHeaders().build(clearOut)) {
            clearArmored.beginClearText(PGPUtil.SHA256);

            // Write raw contents
            clearArmored.write(releaseContents);
            clearArmored.endClearText();

            // Sign exactly the same bytes
            PGPContentSignerBuilder clearSignerBuilder =
                    new JcaPGPContentSignerBuilder(publicKey.getAlgorithm(), PGPUtil.SHA256)
                            .setProvider("BC");
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator(clearSignerBuilder, publicKey);
            sigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privateKey);

            sigGen.update(releaseContents);

            try (BCPGOutputStream bcpgOut = new BCPGOutputStream(clearArmored)) {
                sigGen.generate().encode(bcpgOut);
            }
        }
        result.put("InRelease", clearOut.toString(StandardCharsets.UTF_8));

        // Step 2: Detached signature (Release.gpg)
        ByteArrayOutputStream sigOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredSigOut = ArmoredOutputStream.builder()
                .clearHeaders()
                .build(sigOut)) {

            PGPContentSignerBuilder signerBuilder =
                    new JcaPGPContentSignerBuilder(publicKey.getAlgorithm(), PGPUtil.SHA256)
                            .setProvider("BC");
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator(signerBuilder, publicKey);
            sigGen.init(PGPSignature.BINARY_DOCUMENT, privateKey); // <- BINARY_DOCUMENT for detached sig

            sigGen.update(releaseContents);

            sigGen.generate().encode(armoredSigOut);
        }
        result.put("Release.gpg", sigOut.toString(StandardCharsets.UTF_8));
        return result;
    }
    /*
    public static Map<String, String> signReleaseFile(String armoredPrivateKey, String armoredPublicKey, byte[] releaseContents) throws Exception {
        Map<String, String> result = new HashMap<>();

        // Step 1: Load private key
        PGPSecretKey secretKey = readSigningKey(armoredPrivateKey);
        PGPPrivateKey privateKey = extractPrivateKey(secretKey);  // null passphrase

        result.put("repository.gpg", armoredPublicKey);
        PGPPublicKey publicKey = readPublicKey(armoredPublicKey);

        // Step 2: Detached signature (Release.gpg)
        ByteArrayOutputStream sigOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredSigOut = ArmoredOutputStream.builder()
                .clearHeaders()
                .build(sigOut)) {
            PGPContentSignerBuilder signerBuilder =
                    new JcaPGPContentSignerBuilder(publicKey.getAlgorithm(), PGPUtil.SHA256)
                            .setProvider("BC");
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator(signerBuilder, publicKey);
            sigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privateKey);

            // Write content to signature generator
            try (InputStream in = new ByteArrayInputStream(releaseContents)) {
                int ch;
                while ((ch = in.read()) >= 0) {
                    sigGen.update((byte) ch);
                }
            }

            sigGen.generate().encode(armoredSigOut);
        }
        result.put("Release.gpg", sigOut.toString(StandardCharsets.UTF_8));

        // Step 3: Cleartext signature (InRelease)
        ByteArrayOutputStream clearOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream clearArmored = ArmoredOutputStream.builder().clearHeaders().build(clearOut)) {
            clearArmored.beginClearText(PGPUtil.SHA256);

            // Canonical text line endings
            String[] lines = new String(releaseContents, StandardCharsets.UTF_8).split("\\r?\\n");
            for (String line : lines) {
                byte[] lineBytes = (line + *//*"\r" +*//* "\n").getBytes(StandardCharsets.UTF_8);
                clearArmored.write(lineBytes);
            }

            clearArmored.endClearText();

            // Detached signature again for cleartext
            PGPContentSignerBuilder clearSignerBuilder =
                    new JcaPGPContentSignerBuilder(publicKey.getAlgorithm(), PGPUtil.SHA256)
                            .setProvider("BC");
            PGPSignatureGenerator clearSigGen = new PGPSignatureGenerator(clearSignerBuilder, publicKey);
            clearSigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privateKey);

            for (String line : lines) {
                byte[] canonLine = (line + *//*"\r" +*//* "\n").getBytes(StandardCharsets.UTF_8);
                clearSigGen.update(canonLine);
            }

            try (BCPGOutputStream bcpgOut = new BCPGOutputStream(clearArmored)) {
                clearSigGen.generate().encode(bcpgOut);
            }
        }

        result.put("InRelease", clearOut.toString(StandardCharsets.UTF_8));

        return result;
    }
    */

    private static PGPSecretKey readSigningKey(String armoredKey) throws IOException, PGPException {
        InputStream keyIn = new ByteArrayInputStream(armoredKey.getBytes(StandardCharsets.UTF_8));
        PGPSecretKeyRingCollection keys = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(keyIn),
                new JcaKeyFingerprintCalculator());

        for (PGPSecretKeyRing ring : keys) {
            for (PGPSecretKey key : ring) {
                if (key.isSigningKey()) {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException("No signing key found in key block.");
    }

    private static PGPPrivateKey extractPrivateKey(PGPSecretKey secretKey) throws PGPException {
        PBESecretKeyDecryptor bc = new JcePBESecretKeyDecryptorBuilder()
                .setProvider("BC")
                .build(null);
        return secretKey.extractPrivateKey(bc);
    }
}
