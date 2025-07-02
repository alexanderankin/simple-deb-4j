package deb.simple.build_deb;

import deb.simple.DebArch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildRepositoryTest {

    @Test
    void test_singlePackage() {
        var buildRepository = new BuildRepository();
        var repoBuilder = buildRepository.repoBuilder(Instant.ofEpochMilli(1751437482822L));
        repoBuilder.buildCodeName("jammy")
                .addIndex(new DebPackageMeta()
                        .setSize(10)
                        .setHashes(FileIntegrity.of("hello".getBytes(StandardCharsets.UTF_8), null))
                        .setDebPackageConfig(new DebPackageConfig()
                                .setMeta(new DebPackageConfig.PackageMeta()
                                        .setArch(DebArch.amd64)
                                        .setVersion("0.0.1")
                                        .setName("hello"))
                                .setControl(new DebPackageConfig.ControlExtras()
                                        .setMaintainer("maintainer").setDescription("description"))
                                .setFiles(new DebPackageConfig.DebFileSpec().setDataFiles(List.of()).setControlFiles(List.of()))))
                .build();

        var files = buildRepository.buildRepo(repoBuilder.build());
        var fileStrings = files.entrySet().stream()
                .filter(e -> !e.getValue().getPath().endsWith(".gz"))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new String(e.getValue().getContent(), StandardCharsets.UTF_8)));

        assertEquals(Map.ofEntries(
                Map.entry("main/binary-amd64/Packages", """
                        Package: hello
                        Version: 0.0.1
                        Architecture: amd64
                        Maintainer: maintainer
                        Filename: pool/jammy/hello_0.0.1_amd64.deb
                        Size: 10
                        MD5sum: 5d41402abc4b2a76b9719d911017c592
                        SHA1: aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
                        SHA256: 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
                        SHA512: 9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043
                        Section: main
                        Priority: optional
                        Description: description
                        """),
                Map.entry("Release", """
                        Architectures: amd64
                        Codename: jammy
                        Components: main
                        Date: Wed, 02 Jul 2025 06:24:42 +0000
                        Description: Repository for jammy
                        MD5Sum:
                         e03ad37ef03cbaa936bf4116fc627974              126 Release
                         301672485cd2b2c5f256d6b7fc00a4c5              481 main/binary-amd64/Packages
                         71d83cc9c9ab98e7e88bf80936a579d1              351 main/binary-amd64/Packages.gz
                        SHA1Sum:
                         5b3bc1755d4d7e170002300370d727e7af7e6c1a              126 Release
                         743bace56e8c1701e48d2fd63fb8c18f48b669b4              481 main/binary-amd64/Packages
                         336aade40841d34777f8bb6fc430005c845e887d              351 main/binary-amd64/Packages.gz
                        SHA256Sum:
                         fdaa9ea78dd813665234f1eb1f6a31677ba6eb2a99c44c8fc7ae3eb7a682d209              126 Release
                         246bef0188a8e60855cab8206376c7989b494dcd27d1e91275006a99bd45c339              481 main/binary-amd64/Packages
                         7b196b75a13b6d34b57cf9203e8927ed112333c37c273217746d615f9a7463f7              351 main/binary-amd64/Packages.gz
                        SHA512Sum:
                         0a7ec56ab72e5bc19e65719f73b428ffb361aa9dcf5b73e31416cc3502b2d2b07c0f0166b58d826522892520b744d30fc72f3d4580b9e054f91d625b86a43452              126 Release
                         fa5e46b9949739a155369e26939b5d9e562dd70b81f104391e661e81b78ac1621d25f5db75688af70ee840e7c78560fdabd5acf3fe6067f318744a89b25429dc              481 main/binary-amd64/Packages
                         7bdd10b20d55cb4e9ea9aa83f9942aa13c052026d03e040647f543642421adc897d7c3cb54e46a226116aa7774cd25fbe5ae97ae81a68d43853fb55ec4715735              351 main/binary-amd64/Packages.gz
                        """)
        ), fileStrings);
    }
}
