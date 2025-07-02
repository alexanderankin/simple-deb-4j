package deb.simple.build_deb;

import deb.simple.DebArch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class BuildReleaseTest {

    @Test
    void test() {
        BuildRelease buildRelease = new BuildRelease();
        String release = buildRelease.buildReleaseToString(
                new BuildRepository.Repo.CodenameSection("jammy")
                        .setArches(Set.of(DebArch.amd64))
                        .setComponents(Set.of("main"))
                        .setDate(Instant.ofEpochMilli(1751384453000L))
                        .setPackagesFiles(new HashMap<>(Map.of(
                                "main/binary-amd64/Packages", FileIntegrity.of("hello", null),
                                "main/binary-amd64/Packages.gz", FileIntegrity.of("hello world", null)
                        ))));
        System.out.println(release);
    }

}
