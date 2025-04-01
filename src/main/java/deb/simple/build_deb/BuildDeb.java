package deb.simple.build_deb;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
                        .setContent(config.getControl().render(config.getMeta()))
                        .setPath("control")
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
                    case DebPackageConfig.TarFileSpec.FileTarFileSpec fs -> Files.readAllBytes(Path.of(fs.getSourcePath()));
                    case DebPackageConfig.TarFileSpec.UrlTarFileSpec fs -> downloadUrlTarFile(fs);
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

    private static byte[] downloadUrlTarFile(DebPackageConfig.TarFileSpec.UrlTarFileSpec fs) {
        byte[] result = RestClient.create()
                .get()
                .uri(fs.url)
                .headers(h -> {
                    if (!CollectionUtils.isEmpty(fs.getHeaders()))
                        h.putAll(fs.getHeaders());
                    if (fs.getBearerToken() != null)
                        h.setBearerAuth(fs.getBearerToken());
                })
                .retrieve()
                .body(byte[].class);
        Assert.isTrue(result != null, "have result");
        return result;
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

}
