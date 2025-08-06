package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class BuildRelease {
    static final DateTimeFormatter DATE_R_U = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        // System.out.print('#');
        // System.out.print(new BuildRelease().buildReleaseHeaderToString(Set.of(DebArch.amd64, DebArch.arm64), "jammy", Set.of("main"), Instant.now()));
        // System.out.print('#');
    }

    public String buildReleaseToString(DebRepoConfig config, BuildRepository.Repo.CodenameSection codenameSection) {
        var header = header(config, codenameSection);

        var packagesFiles = codenameSection.packagesFiles();
        var headerIntegrity = FileIntegrity.of(header, "Release");
        packagesFiles.put("Release", headerIntegrity);

        var release = header +
                hashSection("MD5Sum", FileIntegrity::getMd5, packagesFiles) + "\n" +
                hashSection("SHA1", FileIntegrity::getSha1, packagesFiles) + "\n" +
                hashSection("SHA256", FileIntegrity::getSha256, packagesFiles) + "\n" +
                hashSection("SHA512", FileIntegrity::getSha512, packagesFiles) + "\n";

        log.debug("created release: {}", release);
        packagesFiles.put("Release", FileIntegrity.of(release, "Release"));
        return release;
    }

    String header(DebRepoConfig config,
                  BuildRepository.Repo.CodenameSection codenameSection) {
        return """
                Origin: %s
                Label: %s
                Suite: %s
                Codename: %s
                Architectures: %s
                Components: %s
                Date: %s
                Description: Repository for %s
                """
                .formatted(
                        Optional.ofNullable(config.getOrigin()).orElse(codenameSection.getCodename()),
                        Optional.ofNullable(config.getLabel()).orElse(codenameSection.getCodename()),
                        codenameSection.getCodename(),
                        codenameSection.getCodename(),
                        codenameSection.arches().stream().map(DebArch::toString).sorted().collect(Collectors.joining(" ")),
                        codenameSection.components().stream().sorted().collect(Collectors.joining(" ")),
                        DATE_R_U.format(codenameSection.getDate()),
                        codenameSection.getCodename()
                );
    }

    String hashSection(String name, Function<FileIntegrity, String> hashFunction, Map<String, FileIntegrity> files) {
        var header = name + ":\n";

        var lines = files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(file -> " " + hashFunction.apply(file.getValue()) + " " + String.format("%16s", file.getValue().getSize()) + " " + file.getKey())
                .collect(Collectors.joining("\n"));

        return header + lines;
    }
}
