package deb.simple.build_repo;

import deb.simple.DebArch;
import deb.simple.build_deb.DebPackageConfig;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class BuildRepo {
    private static final Pattern SUPPORTED_FIELDS =
            Pattern.compile(
                    "^(Package|Version|Depends|Recommends|Section|Priority" +
                            "|Homepage|Conflicts|Architecture|Installed-Size" +
                            "|Maintainer|Description): ",
                    Pattern.MULTILINE
            );

    public static RepoBuilder buildRepo(byte[]... files) {
        throw new UnsupportedOperationException();
    }

    @Data
    @Accessors(chain = true)
    public static class RepoBuilder {
        List<DebPackageConfig> packages = new ArrayList<>();

        public RepoBuilder addPackage(byte[] contents) {
            DebPackageConfig packageConfig = parse(contents);
            packages.add(packageConfig);
            return this;
        }

        public Packages packages() {
            return new Packages(render(), Collections.unmodifiableList(packages));
        }

        String render() {
            return packages.stream().map(this::render).collect(Collectors.joining("\n\n")) + "\n";
        }

        String render(DebPackageConfig packageConfig) {
            return """
                    Package: %s
                    Version: %s
                    Architecture: %s
                    Maintainer: %s
                    Depends: %s
                    Conflicts: %s
                    Breaks: %s
                    Replaces: %s
                    Filename: %s
                    Section: %s
                    Description: %s"""
                    .formatted(packageConfig.getMeta().getName(),
                            packageConfig.getMeta().getVersion(),
                            packageConfig.getMeta().getArch(),
                            packageConfig.getControl().getMaintainer(),
                            packageConfig.getControl().getDepends(),
                            packageConfig.getControl().getConflicts(),
                            packageConfig.getControl().getBreaks(),
                            packageConfig.getControl().getReplaces(),
                            packageConfig.getMeta().getDebFilename(false),
                            packageConfig.getControl().getSection(),
                            packageConfig.getControl().getDescription()
                    );
            // return """
            //         Package: %s
            //         Version: %s
            //         Architecture: %s
            //         Maintainer: %s
            //         Depends: %s
            //         Conflicts: %s
            //         Breaks: %s
            //         Replaces: %s
            //         Filename: %s
            //         #Size: 0
            //         MD5sum: %s
            //         SHA1: %s
            //         SHA256: %s
            //         Section: %s
            //         Description: %s"""
            //         .formatted(packageConfig.getMeta().getName(),
            //                 packageConfig.getMeta().getVersion(),
            //                 packageConfig.getMeta().getArch(),
            //                 packageConfig.getControl().getMaintainer(),
            //                 packageConfig.getControl().getDepends(),
            //                 packageConfig.getControl().getConflicts(),
            //                 packageConfig.getControl().getBreaks(),
            //                 packageConfig.getControl().getReplaces(),
            //                 packageConfig.getMeta().getDebFilename(false),
            //                 packageConfig.
            //                 );
        }

        @SneakyThrows
        DebPackageConfig parse(byte[] contents) {
            try (ArArchiveInputStream stream = new ArArchiveInputStream(new ByteArrayInputStream(contents))) {
                ArArchiveEntry nextArEntry;

                while (null != (nextArEntry = stream.getNextEntry())) {
                    if (!"control.tar.gz".equals(nextArEntry.getName()))
                        continue;

                    try (var tarArchiveInputStream = new TarArchiveInputStream(new GZIPInputStream(stream))) {
                        TarArchiveEntry nextTarEntry;
                        while ((nextTarEntry = tarArchiveInputStream.getNextEntry()) != null) {
                            if (!"control".equals(nextTarEntry.getName()))
                                continue;

                            var controlContent = IOUtils.toString(tarArchiveInputStream, StandardCharsets.UTF_8);

                            var matcher = SUPPORTED_FIELDS.matcher(controlContent);
                            var results = matcher.results().toList();

                            var config = new DebPackageConfig();
                            config.setMeta(new DebPackageConfig.PackageMeta());
                            config.setControl(new DebPackageConfig.ControlExtras());

                            // var map = new LinkedHashMap<String, String>();
                            for (int i = 0; i < results.size(); i++) {
                                var thisResult = results.get(i);
                                var nextResult = i == results.size() - 1 ? null : results.get(i + 1);
                                var nextEnd = nextResult == null ? controlContent.length() : nextResult.start();

                                var name = thisResult.group(1);
                                var value = controlContent.substring(thisResult.end(), nextEnd - 1);
                                // map.put(name, value);

                                // var location = locations.get(i);
                                // int next = i == locations.size() - 1 ? controlContent.length() : locations.get(i + 1).getValue();
                                //
                                // map.put(location.getKey(), controlContent.substring(location.getValue(), next));

                                switch (name) {
                                    case "Package" -> config.getMeta().setName(value);
                                    case "Version" -> config.getMeta().setVersion(value);
                                    case "Depends" -> config.getControl().setDepends(value);
                                    case "Recommends" -> config.getControl().setRecommends(value);
                                    case "Section" -> config.getControl().setSection(value);
                                    case "Priority" -> config.getControl().setPriority(value);
                                    case "Homepage" -> config.getControl().setHomepage(value);
                                    case "Conflicts" -> config.getControl().setConflicts(value);
                                    case "Architecture" -> config.getMeta().setArch(DebArch.valueOf(value));
                                    // case "Installed-Size" -> config.getControl().setRecommends(value)
                                    case "Maintainer" -> config.getControl().setMaintainer(value);
                                    case "Description" -> config.getControl().setDescription(value);
                                }
                            }

                            return config;
                        }

                        throw new IllegalArgumentException("ar archive had 'control.tar.gz' without 'control' in it");
                    }
                }

                throw new IllegalArgumentException("ar archive did not contain 'control.tar.gz'");
            }
        }
    }

    public record Packages(String packages, List<DebPackageConfig> pkgObjects) {
        @SneakyThrows
        public byte[] packagesGz() {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(packages.getBytes(StandardCharsets.UTF_8)))) {
                return gzipInputStream.readAllBytes();
            }
        }
    }
}
