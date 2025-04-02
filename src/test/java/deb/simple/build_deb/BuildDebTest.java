package deb.simple.build_deb;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import deb.simple.DebArch;
import deb.simple.build_deb.DebPackageConfig.ControlExtras;
import deb.simple.build_deb.DebPackageConfig.DebFileSpec;
import deb.simple.build_deb.DebPackageConfig.PackageMeta;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class BuildDebTest {

    BuildDeb buildDeb = new BuildDeb();
    YAMLMapper yamlMapper = (YAMLMapper) new YAMLMapper().findAndRegisterModules();
    ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    Validator validator = validatorFactory.getValidator();

    <T> T validate(T object) {
        var errors = validator.validate(object);
        if (!errors.isEmpty())
            throw new ConstraintViolationException(errors);
        return object;
    }

    @SneakyThrows
    @Test
    void test_simpleInstall() {
        PackageMeta meta = new PackageMeta()
                .setName("test_simpleInstall")
                .setArch(DebArch.current())
                .setVersion("0.0.1");
        var deb = buildDeb.buildDebToArchive(
                validate(new DebPackageConfig()
                        .setMeta(meta)
                        .setControl(new ControlExtras()
                                .setMaintainer("test_simpleInstall")
                                .setDescription("test_simpleInstall"))
                        .setFiles(new DebFileSpec()
                                .setDataFiles(List.of(new DebPackageConfig.TarFileSpec.TextTarFileSpec()
                                        .setContent("#!/usr/bin/env bash\necho test_simpleInstall")
                                        .setMode(0x755)
                                        .setPath("/usr/bin/test_simpleInstall")))))
        );

        var fileName = meta.getDebFilename();

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(deb), "/tmp/" + fileName);
            genericContainer.start();
            var result = genericContainer.execInContainer("dpkg", "-i", "/tmp/" + fileName);
            System.out.println(result.getStderr());
            System.out.println(result.getStdout());
            assertEquals(0, result.getExitCode());

            result = genericContainer.execInContainer("test_simpleInstall");
            assertEquals("", result.getStderr().strip());
            assertEquals("test_simpleInstall", result.getStdout().strip());
            assertEquals(0, result.getExitCode());
        }
    }

    @SneakyThrows
    @Test
    void test_installingEmptyPackageWithPostInst() {
        var config = validate(yamlMapper.readValue(getClass().getResourceAsStream("simple.yaml"), DebPackageConfig.class));
        config.getMeta().setArch(DebArch.current());
        var fileName = config.getMeta().getDebFilename();
        byte[] archive = new BuildDeb().buildDebToArchive(config);
        assertEquals(1, config.getFiles().getControlFiles().size());
        assertEquals(0x755, config.getFiles().getControlFiles().getFirst().getMode());

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
            genericContainer
                    .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                    .withCopyToContainer(Transferable.of(archive), "/tmp/" + fileName);
            genericContainer.start();
            assertEquals(0, genericContainer.execInContainer("dpkg", "-i", "/tmp/" + fileName).getExitCode());

            var result = genericContainer.execInContainer("cat", "/tmp/simple-postinst");
            assertEquals("", result.getStderr().strip());
            assertEquals("", result.getStdout().strip());
            assertEquals(0, result.getExitCode());
        }
    }

    @SneakyThrows
    @Test
    void test_fileFileType() {
        var config = validate(yamlMapper.readValue(getClass().getResourceAsStream("file-file.yaml"), DebPackageConfig.class));
        config.getMeta().setArch(DebArch.current());
        var fileName = config.getMeta().getDebFilename();

        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path path = fileSystem.getPath("/tmp");
            Files.createDirectories(path);
            Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("my-file")), (path.resolve("my-file")));

            BuildDeb buildDeb = new BuildDeb();
            buildDeb.current = path;
            byte[] archive = buildDeb.buildDebToArchive(config);

            try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")) {
                genericContainer
                        .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                        .withCopyToContainer(Transferable.of(archive), "/tmp/" + fileName);
                genericContainer.start();
                assertEquals(0, genericContainer.execInContainer("dpkg", "-i", "/tmp/" + fileName).getExitCode());
                assertEquals("example content\n", genericContainer.copyFileFromContainer("/etc/file-file/my-file", i -> IOUtils.toString(i, StandardCharsets.UTF_8)));
            }
        }
    }
}
