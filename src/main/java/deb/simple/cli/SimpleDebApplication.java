package deb.simple.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import deb.simple.build_deb.*;
import deb.simple.gpg.GenerateGpgKey;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
                SimpleDebApplication.BuildRepo.class,
                SimpleDebApplication.Gpg.class,
                AutoComplete.GenerateCompletion.class,
        }
)
public class SimpleDebApplication {

    public static void main(String[] args) {
        // args = new String[]{"r", "-i", "/tmp/ubuntu/pool", "-o", "/tmp/tmp.0cXfw8d3QV"};
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
        @Option(names = {"-C"}, description = "change directory before running (defaults to $PWD)")
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
            }
            var deb = new BuildDeb()
                    .setCurrent(current)
                    .buildDeb(config, outDir);
            if (index)
                new BuildIndex().buildDebIndex(deb, config, outDir);
        }
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Data
    @Slf4j
    @Command(name = "repo", aliases = {"r"}, description = "build a debian repository")
    static class BuildRepo implements Runnable {
        @ArgGroup(multiplicity = "1")
        InputGroup inputGroup;

        @Option(names = {"-c", "--codename"}, description = "which codenames to process (or all, if none specified)")
        List<String> codenames;

        @ArgGroup(multiplicity = "1")
        OutputGroup outputGroup;

        @Option(names = {"-r", "--region", "--default-region"})
        String region;

        @SuppressWarnings("RedundantIfStatement")
        boolean hasRegion() {
            var i = inputGroup.getS3();
            if (i != null)
                if (null == Optional.ofNullable(i.getRegion()).orElse(region))
                    return false;
            var o = outputGroup.getS3();
            if (o != null)
                if (null == Optional.ofNullable(o.getRegion()).orElse(region))
                    return false;
            return true;
        }

        public void run() {
            if (!hasRegion())
                throw new RuntimeException("Region not specified - need either default region or input/output -specific region");
            log.info("{}", this);

            var objectMapper = JsonMapper.builder().findAndAddModules().build();
            run(codenames,
                    inputGroup.getInput() == null
                            // read from s3 pool
                            ? s3InputIO(inputGroup.getS3().getUri(), inputGroup.getS3().getRegion(), objectMapper)
                            // read from local filesystem pool
                            : new BuildRepositoryIO.FileBrIo(objectMapper, inputGroup.getInput(), inputGroup.getInput()),
                    outputGroup.getOutput() == null
                            // write to s3 dist
                            ? s3InputIO(outputGroup.getS3().getUri(), outputGroup.getS3().getRegion(), objectMapper)
                            // write to local filesystem dist
                            : new BuildRepositoryIO.FileBrIo(objectMapper, outputGroup.getOutput(), outputGroup.getOutput())
            );
        }

        BuildRepositoryIO.S3BrIo s3InputIO(URI uri, String groupRegion, JsonMapper objectMapper) {
            return new BuildRepositoryIO.S3BrIo(
                    S3Client.builder()
                            .region(Region.of(Objects.requireNonNullElse(groupRegion, region)))
                            .forcePathStyle(true)
                            .build(),
                    objectMapper,
                    uri,
                    uri
            );
        }

        private void run(List<String> codenames, BuildRepositoryIO input, BuildRepositoryIO output) {
            var buildRepository = new BuildRepository();
            var repoBuilder = buildRepository.repoBuilder();

            var builders = input.readMetas();
            List<String> codeNamesFiltered = determineCodenames(codenames, new ArrayList<>(builders.keySet()));
            for (String codename : codeNamesFiltered) {
                var builder = repoBuilder.buildCodeName(codename);
                builders.getOrDefault(codename, List.of()).forEach(builder::addIndex);
                builder.build();
            }

            var repo = repoBuilder.build();
            var files = buildRepository.buildRepo(repo);
            output.writeFiles(files);
        }

        private List<String> determineCodenames(List<String> codenames, List<String> codeNamesFound) {
            List<String> codeNamesFiltered = filterCodeNames(codenames, codeNamesFound);
            log.info("using codename list: {} based on filter list: {}", codeNamesFiltered, codenames);
            if (codenames != null && codeNamesFound.size() != codenames.size())
                log.warn("codenames were specified and filtered down to {} from {}", codeNamesFiltered, codeNamesFound);
            return codeNamesFiltered;
        }

        private List<String> filterCodeNames(List<String> codenames, List<String> codeNamesFound) {
            if (codenames == null || codenames.isEmpty()) {
                return codeNamesFound;
            } else {
                return codeNamesFound.stream().filter(codenames::contains).toList();
            }
        }

        @Data
        static class InputGroup {
            @Option(names = {"-i", "--input", "--input-dir"})
            Path input;
            @ArgGroup(exclusive = false)
            S3InputGroup s3;

            @Data
            static class S3InputGroup {
                @Option(names = {"-s3i", "--s3-input"})
                URI uri;
                @Option(names = {"-ri", "--s3-input-region"}, required = false)
                String region;
            }
        }

        @Data
        static class OutputGroup {
            @Option(names = {"-o", "--output", "--output-dir"})
            Path output;
            @ArgGroup(exclusive = false)
            S3OutputGroup s3;

            @Data
            static class S3OutputGroup {
                @Option(names = {"-s3o", "--s3-output"})
                URI uri;
                @Option(names = {"-ro", "--s3-output-region"}, required = false)
                String region;
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
