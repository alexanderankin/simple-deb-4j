package deb.simple.build_deb;

import deb.simple.build_deb.DebPackageConfig.ControlExtras;
import deb.simple.build_deb.DebPackageConfig.DebFileSpec;
import deb.simple.build_deb.DebPackageConfig.PackageMeta;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class BuildDebTest {

    BuildDeb buildDeb = new BuildDeb();
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
                .setArch("arm64")
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

        try (GenericContainer<?> genericContainer = new GenericContainer<>("debian:12-slim")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("tail", "-f", "/dev/null"))
                .withCopyToContainer(Transferable.of(deb), "/tmp/" + fileName)) {
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
}
