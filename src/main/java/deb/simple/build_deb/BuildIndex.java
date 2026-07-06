package deb.simple.build_deb;

import lombok.SneakyThrows;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class BuildIndex {
    JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

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
