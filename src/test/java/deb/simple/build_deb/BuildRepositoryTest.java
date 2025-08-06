package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class BuildRepositoryTest {

    @Test
    void test_singlePackage() {
        var buildRepository = new BuildRepository();
        var repoBuilder = buildRepository.repoBuilder(new DebRepoConfig(), Instant.ofEpochMilli(1751437482822L));
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
                Map.entry("jammy/main/binary-amd64/Packages", """
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
                Map.entry("jammy/Release", """
                        Origin: jammy
                        Label: jammy
                        Suite: jammy
                        Codename: jammy
                        Architectures: amd64
                        Components: main
                        Date: Wed, 02 Jul 2025 06:24:42 +0000
                        Description: Repository for jammy
                        MD5Sum:
                         5459e389d9457bfffafc30349c24996d              166 Release
                         301672485cd2b2c5f256d6b7fc00a4c5              481 main/binary-amd64/Packages
                         71d83cc9c9ab98e7e88bf80936a579d1              351 main/binary-amd64/Packages.gz
                        SHA1:
                         edf3e29b4618a6dd7ca79211f14701feaf47b0b4              166 Release
                         743bace56e8c1701e48d2fd63fb8c18f48b669b4              481 main/binary-amd64/Packages
                         336aade40841d34777f8bb6fc430005c845e887d              351 main/binary-amd64/Packages.gz
                        SHA256:
                         9c0f546b1379c1005950dcce1032c784955b218262a53a777321ac9cae836762              166 Release
                         246bef0188a8e60855cab8206376c7989b494dcd27d1e91275006a99bd45c339              481 main/binary-amd64/Packages
                         7b196b75a13b6d34b57cf9203e8927ed112333c37c273217746d615f9a7463f7              351 main/binary-amd64/Packages.gz
                        SHA512:
                         fe7d6077c1fb44d816230e9ca39a3191a8eba9b19c07314021f12247a0fb35ee44bbf34f17d13fd56078925c801b379711411cc6dfe97968c35bc8f38e6b2e95              166 Release
                         fa5e46b9949739a155369e26939b5d9e562dd70b81f104391e661e81b78ac1621d25f5db75688af70ee840e7c78560fdabd5acf3fe6067f318744a89b25429dc              481 main/binary-amd64/Packages
                         7bdd10b20d55cb4e9ea9aa83f9942aa13c052026d03e040647f543642421adc897d7c3cb54e46a226116aa7774cd25fbe5ae97ae81a68d43853fb55ec4715735              351 main/binary-amd64/Packages.gz
                        """)
        ), fileStrings);
    }

    @SneakyThrows
    @Test
    void test_installable() {
        var commonPackage = new BuildDebTest().validate(new DebPackageConfig()
                .setMeta(new DebPackageConfig.PackageMeta().setName("common-package").setVersion("0.0.1").setArch(DebArch.current()))
                .setControl(new DebPackageConfig.ControlExtras().setMaintainer("maintainer").setDescription("description"))
                .setFiles(new DebPackageConfig.DebFileSpec()
                        .setDataFiles(List.of(new DebPackageConfig.TarFileSpec.TextTarFileSpec()
                                .setContent("#!/usr/bin/env bash\necho '0.0.1'\n")
                                .setPath("/usr/bin/common-package")
                                .setMode(0x755))))
        );

        var jqVersionReporter = new BuildDebTest().validate(new DebPackageConfig()
                .setMeta(new DebPackageConfig.PackageMeta().setName("jq-version-reporter").setVersion("0.0.1").setArch(DebArch.current()))
                .setControl(new DebPackageConfig.ControlExtras().setMaintainer("maintainer").setDescription("description").setDepends("common-package").setConflicts("jq-vr2"))
                .setFiles(new DebPackageConfig.DebFileSpec()
                        .setDataFiles(List.of(new DebPackageConfig.TarFileSpec.TextTarFileSpec()
                                .setContent("#!/usr/bin/env bash\necho 'welcome to jq-version-reporter'\ncommon-package\n")
                                .setPath("/usr/bin/jqr")
                                .setMode(0x755))))
        );

        var jqVR2 = new BuildDebTest().validate(new DebPackageConfig()
                .setMeta(new DebPackageConfig.PackageMeta().setName("jq-vr2").setVersion("0.0.1").setArch(DebArch.current()))
                .setControl(new DebPackageConfig.ControlExtras().setMaintainer("maintainer").setDescription("description").setDepends("common-package").setConflicts("jq-version-reporter"))
                .setFiles(new DebPackageConfig.DebFileSpec()
                        .setDataFiles(List.of(new DebPackageConfig.TarFileSpec.TextTarFileSpec()
                                .setContent("#!/usr/bin/env bash\necho 'welcome to jq-vr2'\ncommon-package\n")
                                .setPath("/usr/bin/jqr")
                                .setMode(0x755))))
        );

        BuildDeb buildDeb = new BuildDeb();
        byte[] commonPackageArchive = buildDeb.buildDebToArchive(commonPackage);
        byte[] jqVersionReporterArchive = buildDeb.buildDebToArchive(jqVersionReporter);
        byte[] jqVR2Archive = buildDeb.buildDebToArchive(jqVR2);

        BuildIndex buildIndex = new BuildIndex();
        var commonPackageIndex = buildIndex.buildDebIndexToDto(commonPackageArchive, commonPackage);
        var jqVersionReporterIndex = buildIndex.buildDebIndexToDto(jqVersionReporterArchive, jqVersionReporter);
        var jqVR2Index = buildIndex.buildDebIndexToDto(jqVR2Archive, jqVR2);

        List<Map.Entry<DebPackageConfig, byte[]>> debFiles = List.of(
                Map.entry(commonPackage, commonPackageArchive),
                Map.entry(jqVersionReporter, jqVersionReporterArchive),
                Map.entry(jqVR2, jqVR2Archive)
        );

        BuildRepository buildRepository = new BuildRepository();
        var repo = buildRepository.repoBuilder(new DebRepoConfig(), Instant.ofEpochMilli(1751384953000L))
                .buildCodeName("bullseye").addIndex(commonPackageIndex).addIndex(jqVersionReporterIndex).addIndex(jqVR2Index).build()
                .buildCodeName("bookworm").addIndex(commonPackageIndex).addIndex(jqVersionReporterIndex).addIndex(jqVR2Index).build()
                .build();
        var repoFiles = buildRepository.buildRepo(repo);

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"));

            // https://askubuntu.com/questions/170348/how-to-create-a-local-apt-repository
            genericContainer.withCopyToContainer(Transferable.of("deb [trusted=yes] file:/tmp/repo bullseye main"), "/etc/apt/sources.list");

            for (var repoFile : repoFiles.entrySet()) {
                genericContainer.withCopyToContainer(Transferable.of(repoFile.getValue().getContent()), "/tmp/repo/dists/" + repoFile.getKey());
            }
            for (String codename : repo.getCodenameSectionMap().keySet()) {
                for (var debFile : debFiles) {
                    genericContainer.withCopyToContainer(Transferable.of(debFile.getValue()), "/tmp/repo/pool/" + codename + "/" + debFile.getKey().getMeta().getDebFilename());
                }
            }

            genericContainer.start();
            assertEquals(0, execInContainer(genericContainer, "rm", "-rf", "/etc/apt/sources.list.d").getExitCode());
            assertEquals(0, execInContainer(genericContainer, "apt", "update").getExitCode());
            assertEquals(0, execInContainer(genericContainer, "apt", "install", "-y", jqVersionReporter.getMeta().getName()).getExitCode());
            assertEquals(0, execInContainer(genericContainer, "jqr").getExitCode());
            assertThat(execInContainer(genericContainer, "jqr").getStdout(), containsString(jqVersionReporter.getMeta().getName()));

            assertEquals(0, execInContainer(genericContainer, "apt", "install", "-y", jqVR2.getMeta().getName()).getExitCode());
            assertEquals(0, execInContainer(genericContainer, "jqr").getExitCode());
            assertThat(execInContainer(genericContainer, "jqr").getStdout(), containsString(jqVR2.getMeta().getName()));
        }
    }

    @SneakyThrows
    Container.ExecResult execInContainer(GenericContainer<?> container, String... command) {
        Container.ExecResult result = container.execInContainer(command);
        log.info("running command {} gave {}, with stdout: {} and stderr: {}", command, result.getExitCode(), result.getStdout(), result.getStderr());
        return result;
    }

    /*
    @SneakyThrows
    @Test
    void test() {
        HashMap<String, FileIntegrity> files = new HashMap<>(Map.of(
                "Release",
                FileIntegrity.of(Files.readAllBytes(Path.of("/home/toor/priv-sign")), "Release")
        ));
        new BuildRepository()
                .signFiles(
                        files,
                        Files.readString(Path.of("/home/toor/priv"), StandardCharsets.UTF_8),
                        Files.readString(Path.of("/home/toor/privpub"), StandardCharsets.UTF_8)
                );

        assertThat(files.entrySet(), hasSize(3));

        var headers = files.values().stream()
                .map(FileIntegrity::getContent)
                .map(String::new)
                .filter(e -> e.startsWith("-----"))
                .map(e -> e.split("\n")[0])
                .collect(Collectors.toSet());

        assertThat(headers, is(Set.of("-----BEGIN PGP SIGNED MESSAGE-----", "-----BEGIN PGP SIGNATURE-----")));
    }
    */
}
