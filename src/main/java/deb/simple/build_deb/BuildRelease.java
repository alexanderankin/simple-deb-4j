package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildRelease {
    static final DateTimeFormatter DATE_R_U = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        // System.out.print('#');
        // System.out.print(new BuildRelease().buildReleaseHeaderToString(Set.of(DebArch.amd64, DebArch.arm64), "jammy", Set.of("main"), Instant.now()));
        // System.out.print('#');
    }

    public String buildReleaseToString(RepoRelease repoRelease) {
        var headerAndHashes = repoRelease.headerAndHashes();

        /*
            the files array has are:
            * Release
            * main/binary-amd64/Packages
            * main/binary-amd64/Packages.gz
            * main/binary-arm64/Packages
            * main/binary-arm64/Packages.gz
         */

        var files = new LinkedHashMap<String, FileIntegrity>();
        files.put("Release", headerAndHashes);

        var hashes = repoRelease.getPackagesHashes();
        repoRelease.getArches().stream().sorted().forEach(debArch -> {
            String packages = "main/binary-" + debArch.name() + "/Packages";
            files.put(packages, Objects.requireNonNull(hashes.get(packages)));

            String packagesGz = "main/binary-" + debArch.name() + "/Packages.gz";
            files.put(packagesGz, Objects.requireNonNull(hashes.get(packagesGz)));
        });

        return headerAndHashes.content +
                hashSection("MD5Sum", FileIntegrity::getMd5, files) + "\n" +
                hashSection("SHA1Sum", FileIntegrity::getSha1, files) + "\n" +
                hashSection("SHA256Sum", FileIntegrity::getSha256, files) + "\n" +
                hashSection("SHA512Sum", FileIntegrity::getSha512, files);
    }

    String hashSection(String name, Function<FileIntegrity, String> hashFunction, Map<String, FileIntegrity> files) {
        var header = name + ":\n";

        var lines = files.entrySet().stream()
                .map(file -> " " + hashFunction.apply(file.getValue()) + " " + String.format("%16s", file.getValue().getSize()) + " " + file.getKey())
                .collect(Collectors.joining("\n"));

        return header + lines;
    }

    @Data
    @Accessors(chain = true)
    public static class RepoRelease {
        String codename;
        Instant now;
        Collection<DebArch> arches;
        Collection<String> components;
        Map<String, FileIntegrity> packagesHashes;

        String header() {
            return """
                    Architectures: %s
                    Codename: %s
                    Components: main
                    Date: %s
                    Description: Repository for %s
                    """
                    .formatted(
                            arches.stream().map(DebArch::toString).sorted().collect(Collectors.joining(" ")),
                            codename,
                            DATE_R_U.format(now),
                            codename
                    );
        }

        FileIntegrity headerAndHashes() {
            var header = header();
            return new FileIntegrity()
                    .setContent(header)
                    .setMd5(DigestUtils.md5Hex(header))
                    .setSha1(DigestUtils.sha1Hex(header))
                    .setSha256(DigestUtils.sha256Hex(header))
                    .setSha512(DigestUtils.sha512Hex(header));
        }
    }
}
