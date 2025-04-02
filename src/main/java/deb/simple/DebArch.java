package deb.simple;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * as reported by `dpkg-architecture -L --match-bits 64 --match-endian little --match-wildcard gnu-linux-any`
 */
@Getter
@RequiredArgsConstructor
public enum DebArch {
    amd64("amd64"),
    arm64("aarch64"),
    ;

    private static final Map<String, DebArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(DebArch::getJdkArchName, Function.identity()));

    private final String jdkArchName;

    public static DebArch current() {
        return MAP.get(System.getProperty("os.arch"));
    }
}
