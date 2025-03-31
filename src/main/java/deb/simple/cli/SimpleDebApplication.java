package deb.simple.cli;

import com.fasterxml.jackson.databind.json.JsonMapper;
import deb.simple.build_deb.BuildDeb;
import deb.simple.build_deb.BuildDeb.DebPackageConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Slf4j
@Command(
        name = "simple-deb",
        description = "simple debian packaging utility",
        version = "0.0.1",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        scope = CommandLine.ScopeType.INHERIT,
        subcommands = {
                SimpleDebApplication.Build.class,
                AutoComplete.GenerateCompletion.class,
        }
)
public class SimpleDebApplication {

    public static void main(String[] args) {
        int exitCode = new CommandLine(SimpleDebApplication.class).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "build", aliases = {"b"}, description = "build a debian package")
    static class Build implements Runnable {
        @Option(names = {"-c", "--config"}, description = "configuration file")
        Path configFile;
        @Option(names = {"-o", "--output"}, description = "output directory")
        Path outDir;

        @SneakyThrows
        @Override
        public void run() {
            var mapper = JsonMapper.builder().findAndAddModules().build();
            var config = mapper.readValue(configFile.toFile(), DebPackageConfig.class);
            try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
                var errors = validatorFactory.getValidator().validate(config);
                if (!errors.isEmpty())
                    throw new ConstraintViolationException(errors);
                new BuildDeb().buildDeb(config, outDir);
            }
        }
    }

}
