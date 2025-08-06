package deb.simple.build_deb;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;

public class BuildIndex {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @SuppressWarnings("UnusedReturnValue")
    @SneakyThrows
    public byte[] buildDebIndex(byte[] deb, DebPackageConfig config, Path outDir) {
        byte[] index = buildDebIndexToBytes(deb, config);
        Files.write(outDir.resolve(config.getMeta().getIndexFilename()), index);
        return index;
    }

    @SneakyThrows
    public byte[] buildDebIndexToBytes(byte[] deb, DebPackageConfig config) {
        return objectMapper.writeValueAsBytes(buildDebIndexToDto(deb, config));
    }

    public DebPackageMeta buildDebIndexToDto(byte[] deb, DebPackageConfig config) {
        return new DebPackageMeta()
                .setDebPackageConfig(config)
                .setHashes(FileIntegrity.of(deb, null))
                .setSize(deb.length);
    }
}
