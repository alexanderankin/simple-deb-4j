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
                Architectures: amd64
                Codename: jammy
                Components: main
                Date: Tue, 01 Jul 2025 15:40:53 +0000
                Description: Repository for jammy
                MD5Sum:
                 105232331d3a85224f183665648f4c0f              126 Release
                 5d41402abc4b2a76b9719d911017c592                5 main/binary-amd64/Packages
                 5eb63bbbe01eeed093cb22bb8f5acdc3               11 main/binary-amd64/Packages.gz
                SHA1:
                 7cc8a25ce6b87df652ae9020ab125c60382f3d6d              126 Release
                 aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d                5 main/binary-amd64/Packages
                 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed               11 main/binary-amd64/Packages.gz
                SHA256:
                 eb755b14d3b549d05217d9760dfef4639671df29c466cb32e69f145361455503              126 Release
                 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824                5 main/binary-amd64/Packages
                 b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9               11 main/binary-amd64/Packages.gz
                SHA512:
                 6fa15776a01940917f90da3dbdb274ef0306e0f3a6d7584c839ef51eab7c203acd3fca64d56b682315d5bdbf449249dad0cce7b0d1aab0ae147f14b47d55517d              126 Release
                 9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043                5 main/binary-amd64/Packages
                 309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f               11 main/binary-amd64/Packages.gz
                """, release);
    }

}
