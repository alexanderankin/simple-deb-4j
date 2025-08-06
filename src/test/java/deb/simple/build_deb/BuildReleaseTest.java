package deb.simple.build_deb;

import deb.simple.DebArch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildReleaseTest {

    @Test
    void test() {
        BuildRelease buildRelease = new BuildRelease();
        String release = buildRelease.buildReleaseToString(
                new DebRepoConfig(),
                new BuildRepository.Repo.CodenameSection("jammy")
                        .setArches(Set.of(DebArch.amd64))
                        .setComponents(Set.of("main"))
                        .setDate(Instant.ofEpochMilli(1751384453000L))
                        .setPackagesFiles(new HashMap<>(Map.of(
                                "main/binary-amd64/Packages", FileIntegrity.of("hello", null),
                                "main/binary-amd64/Packages.gz", FileIntegrity.of("hello world", null)
                        ))));
        assertEquals("""
                Origin: jammy
                Label: jammy
                Suite: jammy
                Codename: jammy
                Architectures: amd64
                Components: main
                Date: Tue, 01 Jul 2025 15:40:53 +0000
                Description: Repository for jammy
                MD5Sum:
                 536dbb9c4682fd0a3cb558e03b253b20              166 Release
                 5d41402abc4b2a76b9719d911017c592                5 main/binary-amd64/Packages
                 5eb63bbbe01eeed093cb22bb8f5acdc3               11 main/binary-amd64/Packages.gz
                SHA1:
                 bc055c5bfa4aac4639bb18aa8fa22b3cf6602d04              166 Release
                 aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d                5 main/binary-amd64/Packages
                 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed               11 main/binary-amd64/Packages.gz
                SHA256:
                 7c5bf294d9fb8dc71ca1476e6e62f99a6f07f1067f12d69ab56ce097e2560f68              166 Release
                 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824                5 main/binary-amd64/Packages
                 b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9               11 main/binary-amd64/Packages.gz
                SHA512:
                 9c0ab06b86c4c4de885e690a1f006b5a2214fdf142b7eea21cf5483fcf16ec48f13b9790539d960da1a3fdf17f3ad53b9b74aa34c06f97d124a2d986f375bc60              166 Release
                 9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043                5 main/binary-amd64/Packages
                 309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f               11 main/binary-amd64/Packages.gz
                """, release);
    }

}
