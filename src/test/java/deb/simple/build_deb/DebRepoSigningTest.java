package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class DebRepoSigningTest {

    static List<DistroInfo> supportedDistros() {
        return List.of(
                new DistroInfo("debian:12-slim", "bullseye"),
                new DistroInfo("debian:13-slim", "trixie"),
                new DistroInfo("ubuntu:24.04", "noble"),
                new DistroInfo("ubuntu:26.04", "resolute"));
    }

    @Test
    void test() {
        var packageMeta = new DebPackageMeta()
                .setSize(10)
                .setHashes(FileIntegrity.of("hello".getBytes(StandardCharsets.UTF_8), null))
                .setDebPackageConfig(new DebPackageConfig()
                        .setMeta(new DebPackageConfig.PackageMeta()
                                .setArch(DebArch.current())
                                .setVersion("0.0.1")
                                .setName("hello"))
                        .setControl(new DebPackageConfig.ControlExtras()
                                .setMaintainer("maintainer").setDescription("description"))
                        .setFiles(new DebPackageConfig.DebFileSpec().setDataFiles(List.of()).setControlFiles(List.of())));
        var packageBytes = new BuildDeb().buildDebToArchive(packageMeta.getDebPackageConfig());
        packageMeta.setHashes(FileIntegrity.of(packageBytes, null));
        packageMeta.setSize(packageBytes.length);

        var buildRepository = new BuildRepository();
        var repoBuilder = buildRepository.repoBuilder(new DebRepoConfig(), Instant.ofEpochMilli(1751437482822L));

        for (var distro : supportedDistros())
            repoBuilder.buildCodeName(distro.codeName).addIndex(packageMeta).build();

        var files = buildRepository.buildRepo(repoBuilder.build());
        var fileStrings = files.entrySet().stream()
                .filter(e -> !e.getValue().getPath().endsWith(".gz"))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new String(e.getValue().getContent(), StandardCharsets.UTF_8)));

        System.out.println(fileStrings.entrySet().stream().filter(e -> Arrays.asList(e.getKey().split("/")).getLast().equals("Release"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        var debRepoSigning = new DebRepoSigning();
        var signingKey = debRepoSigning.genKey("user <user@host.tld>");
        var signedFiles = new HashMap<String, String>();
        for (var f : files.entrySet()) {
            var parts = Arrays.asList(f.getKey().split("/"));
            if (parts.getLast().equals("Release")) {
                var signedRelease = debRepoSigning.signRelease(f.getValue().getContent(), signingKey.getPrivateKey());
                signedFiles.put(String.join("/", parts.subList(0, parts.size() - 1)) + "/InRelease", signedRelease.getInRelease());
                signedFiles.put(String.join("/", parts.subList(0, parts.size() - 1)) + "/Release.gpg", signedRelease.getReleaseGpg());
            }
        }

        for (var distro : supportedDistros())
            try (GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(distro.dockerImage))) {
                c.withCreateContainerCmdModifier(cc -> cc.withEntrypoint("tail", "-f", "/dev/null"));
                c.withEnv("DEBIAN_FRONTEND", "noninteractive");
                for (var f : files.entrySet()) {
                    c.withCopyToContainer(Transferable.of(f.getValue().getContent()), "/tmp/repo/dists/" + f.getKey());
                }
                for (var s : signedFiles.entrySet()) {
                    c.withCopyToContainer(Transferable.of(s.getValue()), "/tmp/repo/dists/" + s.getKey());
                }
                c.withCopyToContainer(Transferable.of(packageBytes), "/tmp/repo/pool/" + distro.codeName + "/" + packageMeta.getDebPackageConfig().getMeta().getDebFilename());

                // c.withCopyToContainer(Transferable.of(signingKey.getPublicKey()), "/tmp/repo/repository.gpg");
                c.withCopyToContainer(Transferable.of(signingKey.getPublicKey()), "/tmp/repo/repository.asc");

                c.start();
                assertEquals(0, execInContainer(c, "rm", "-rf", "/etc/apt/sources.list", "/etc/apt/sources.list.d").getExitCode());
                assertEquals(0, execInContainer(c, "sh", "-c", "echo 'deb [signed-by=/tmp/repo/repository.asc] file:/tmp/repo " + distro.codeName + " main' > /etc/apt/sources.list").getExitCode());
                assertEquals(0, execInContainer(c, "apt-get", "update").getExitCode());
                assertEquals(0, execInContainer(c, "apt-get", "install", "-y", "hello").getExitCode());
            }
    }

    @SneakyThrows
    Container.ExecResult execInContainer(GenericContainer<?> container, String... command) {
        Container.ExecResult result = container.execInContainer(command);
        log.info("running command {} gave {}, with stdout: {} and stderr: {}", command, result.getExitCode(), result.getStdout(), result.getStderr());
        return result;
    }

    record DistroInfo(String dockerImage, String codeName) {
    }

}
