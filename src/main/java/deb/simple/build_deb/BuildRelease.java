package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
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

    public String buildReleaseToString(BuildRepository.Repo.CodenameSection codenameSection) {
        var header = header(codenameSection);

        var packagesFiles = codenameSection.packagesFiles();
        var headerIntegrity = FileIntegrity.of(header, "Release");
        packagesFiles.put("Release", headerIntegrity);

        var release = header +
                hashSection("MD5Sum", FileIntegrity::getMd5, packagesFiles) + "\n" +
                hashSection("SHA1Sum", FileIntegrity::getSha1, packagesFiles) + "\n" +
                hashSection("SHA256Sum", FileIntegrity::getSha256, packagesFiles) + "\n" +
                hashSection("SHA512Sum", FileIntegrity::getSha512, packagesFiles) + "\n";

        log.debug("created release: {}", release);
        headerIntegrity.setContent(release.getBytes(StandardCharsets.UTF_8));
        return release;
    }

    String header(BuildRepository.Repo.CodenameSection codenameSection) {
        return """
                Architectures: %s
                Codename: %s
                Components: main
                Date: %s
                Description: Repository for %s
                """
                .formatted(
                        codenameSection.arches().stream().map(DebArch::toString).sorted().collect(Collectors.joining(" ")),
                        codenameSection.getCodename(),
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
