package deb.simple.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import deb.simple.build_deb.BuildDeb;
import deb.simple.build_deb.DebPackageConfig;
import deb.simple.gpg.GenerateGpgKey;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Command(
        name = "simple-deb",
        description = "simple debian packaging and repository utility",
        versionProvider = ManifestVersionProvider.class,
        mixinStandardHelpOptions = true,
        sortOptions = false,
        showDefaultValues = true,
        scope = CommandLine.ScopeType.INHERIT,
        subcommands = {
                SimpleDebApplication.Build.class,
                SimpleDebApplication.Gpg.class,
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
        @Option(names = {"-i", "--index"}, description = "produce index package - suitable for indexing but not installable")
        boolean index = false;
        @Option(names = {"-C"}, description = "change directory before running", defaultValue = "$PWD")
        Path current = Path.of(System.getProperty("user.dir"));

        @SneakyThrows
        @Override
        public void run() {
            var mapper = JsonMapper.builder().findAndAddModules().build();
            var yamlMapper = YAMLMapper.builder().findAndAddModules().build();
            DebPackageConfig config;
            try {
                config = mapper.readValue(configFile.toFile(), DebPackageConfig.class);
            } catch (JsonProcessingException jpe) {
                config = yamlMapper.readValue(configFile.toFile(), DebPackageConfig.class);
            }
            try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
                var errors = validatorFactory.getValidator().validate(config);
                if (!errors.isEmpty())
                    throw new ConstraintViolationException(errors);
                new BuildDeb()
                        .setCurrent(current)
                        .buildDeb(config, outDir, index);
            }
        }
    }

    @Command(name = "gpg", aliases = {"g"}, description = "gpg functions", subcommands = {
            Gpg.GpgGenKey.class
    })
    static class Gpg {
        @Command(name = "gen-key", aliases = {"gk"}, description = "generate gpg key")
        static class GpgGenKey implements Runnable {
            @CommandLine.Parameters(description = "name", index = "0")
            String name;

            @CommandLine.Parameters(description = "email", index = "1")
            String email;

            @Option(names = {"-l", "--length"}, defaultValue = "_4096", description = "RSA key length (${COMPLETION-CANDIDATES})")
            RsaLength rsaLength = RsaLength._4096;

            @Option(names = {"-P", "--output-public"}, description = "file to write public key to")
            Path outputPublic;

            @Option(names = {"-K", "--output-private"}, description = "file to write private key to")
            Path outputPrivate;

            @SneakyThrows
            @Override
            public void run() {
                var result = new GenerateGpgKey().genGpg(name, email, rsaLength);

                if (outputPublic != null) {
                    Files.writeString(outputPublic, result.getPublicKey());
                } else {
                    System.out.println(result.getPublicKey());
                }

                if (outputPrivate != null) {
                    Files.writeString(outputPrivate, result.getPrivateKey());
                } else {
                    System.out.println(result.getPrivateKey());
                }
            }
        }
    }
}
