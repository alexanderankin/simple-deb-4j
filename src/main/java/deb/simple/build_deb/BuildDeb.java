package deb.simple.build_deb;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class BuildDeb {
    @SneakyThrows
    public void buildDeb(DebPackageConfig config, Path outDir) {
        byte[] arArchive = buildDebToArchive(config);

        Path output = outDir.resolve(config.getMeta().getDebFilename());
        Files.createDirectories(outDir);
        Files.write(output, arArchive);

        log.info("Created .deb package: {}", output);
    }

    public byte[] buildDebToArchive(DebPackageConfig config) {
        byte[] controlTarGz = createTarGz(
                Optional.ofNullable(config.getFiles().getControlFiles()).orElseGet(List::of),
                List.of(new DebPackageConfig.TarFileSpec.TextTarFileSpec()
                        .setPath("control")
                        .setContent(config.getControl().render(config.getMeta()))
                        .setMode(null))
        );

        byte[] dataTarGz = createTarGz(
                Optional.ofNullable(config.getFiles().getDataFiles()).orElseGet(List::of),
                List.of()
        );

        return createArArchive(List.of(
                Map.entry("debian-binary", "2.0\n".getBytes()),
                Map.entry("control.tar.gz", controlTarGz),
                Map.entry("data.tar.gz", dataTarGz)
        ));
    }

    @SneakyThrows
    private byte[] createTarGz(List<DebPackageConfig.TarFileSpec> files, List<DebPackageConfig.TarFileSpec> extra) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            List<DebPackageConfig.TarFileSpec> allFiles = new ArrayList<>(files);
            allFiles.addAll(extra);

            for (DebPackageConfig.TarFileSpec f : allFiles) {

                byte[] content = switch (f) {
                    case DebPackageConfig.TarFileSpec.TextTarFileSpec text -> text.getContent().getBytes();
                    case DebPackageConfig.TarFileSpec.BinaryTarFileSpec bin -> bin.getContent();
                };
                TarArchiveEntry entry = new TarArchiveEntry(f.getPath());
                entry.setSize(content.length);

                if (f.getMode() != null) {
                    entry.setMode(f.getMode());
                }

                tarOut.putArchiveEntry(entry);
                tarOut.write(content);
                tarOut.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * order matters to debian packaging
     */
    @SneakyThrows
    private byte[] createArArchive(List<Map.Entry<String, byte[]>> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArArchiveOutputStream arOut = new ArArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries) {
                String name = entry.getKey();
                byte[] content = entry.getValue();
                ArArchiveEntry arEntry = new ArArchiveEntry(name, content.length);
                arOut.putArchiveEntry(arEntry);
                arOut.write(content);
                arOut.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    @Data
    @Accessors(chain = true)
    public static class DebPackageConfig {
        @Valid
        PackageMeta meta;
        @Valid
        ControlExtras control;
        @Valid
        DebFileSpec files;

        sealed interface TarFileSpec {
            String getPath();

            Integer getMode();

            @Data
            @Accessors(chain = true)
            final class TextTarFileSpec implements TarFileSpec {
                String path;
                String content;
                Integer mode;
            }

            @Data
            @Accessors(chain = true)
            final class BinaryTarFileSpec implements TarFileSpec {
                String path;
                byte[] content;
                Integer mode;
            }
        }

        @Data
        @Accessors(chain = true)
        public static class PackageMeta {
            @NotBlank
            String name;
            @NotBlank
            String version;
            /**
             * todo make me an enum as reported by `dpkg-architecture -L --match-bits 64 --match-endian little --match-wildcard linux-any`
             */
            @NotBlank
            String arch;

            public String getDebFilename() {
                return name + "_" + version + "_" + arch + ".deb";
            }
        }

        @Data
        @Accessors(chain = true)
        public static class ControlExtras {
            @NotNull
            String depends = "";
            @NotNull
            String recommends = "";
            @NotNull
            String section = "main";
            @NotNull
            String priority = "optional";
            @NotNull
            String homepage = "";
            @NotBlank
            String maintainer = "";
            @NotBlank
            String description = "";

            public String render(PackageMeta meta) {
                return String.format("""
                                Package: %s
                                Version: %s
                                Depends: %s
                                Recommends: %s
                                Section: %s
                                Priority: %s
                                Homepage: %s
                                Architecture: %s
                                Installed-Size: 10
                                Maintainer: %s
                                Description: %s
                                """,
                        meta.getName(), meta.getVersion(), depends, recommends, section, priority,
                        homepage, meta.getArch(), maintainer, description
                ).strip() + "\n";
            }
        }

        @Data
        @Accessors(chain = true)
        public static class DebFileSpec {
            List<TarFileSpec> controlFiles;
            List<TarFileSpec> dataFiles;
        }
    }
}
