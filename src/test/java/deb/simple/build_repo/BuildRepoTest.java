/*
package deb.simple.build_repo;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.jimfs.Jimfs;
import deb.simple.DebArch;
import deb.simple.build_deb.BuildDeb;
import deb.simple.build_deb.DebPackageConfig;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.nio.file.FileSystem;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildRepoTest {

    YAMLMapper yamlMapper = new YAMLMapper();
    BuildDeb buildDeb = new BuildDeb();

    @SneakyThrows
    @BeforeEach
    void setup() {
        @SuppressWarnings("resource")
        FileSystem fileSystem = Jimfs.newFileSystem();
        buildDeb.setCurrent(fileSystem.getPath("/"));
        Files.createDirectories(buildDeb.getCurrent());
    }

    @SneakyThrows
    @AfterEach
    void cleanup() {
        buildDeb.getCurrent().getFileSystem().close();
    }

    @SneakyThrows
    @Test
    void test_producesFileRepo() {
        DebPackageConfig config = yamlMapper.readValue(getClass().getResourceAsStream("hello.yaml"), DebPackageConfig.class);
        config.getMeta().setArch(DebArch.current());
        byte[] full = buildDeb.buildDebToArchive(config);
        byte[] index = buildDeb.buildDebToArchive(config, true);

        try (GenericContainer<?> container = new GenericContainer<>()) {
            container
                    .withCopyToContainer(Transferable.of(full), "/tmp/" + config.getMeta().getDebFilename(false))
                    .withCopyToContainer(Transferable.of(index), "/tmp/" + config.getMeta().getDebFilename(true))
                    .withCopyToContainer(Transferable.of("deb [arch=amd64 trusted=yes] file:///tmp ./"), "/tmp/" + config.getMeta().getDebFilename(true))
                    .start();

            assertEquals(0, container.execInContainer("apt", "update").getExitCode());
            assertEquals(0, container.execInContainer("apt", "update").getExitCode());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
            "hello.yaml",
            "hello-multiline.yaml",
    })
    void test_parse(String name) {
        DebPackageConfig config = yamlMapper.readValue(getClass().getResourceAsStream(name), DebPackageConfig.class);
        byte[] index = buildDeb.buildDebToArchive(config, true);
        DebPackageConfig parsed = new BuildRepo.RepoBuilder().parse(index);
        assertEquals(config.getControl(), parsed.getControl());
        assertEquals(config.getMeta(), parsed.getMeta());
    }
}
*/
