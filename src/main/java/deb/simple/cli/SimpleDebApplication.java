package deb.simple.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import deb.simple.build_deb.*;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    @Slf4j
    @Command(name = "repo", aliases = {"r"}, description = "build a debian repository")
    static class BuildRepo {
        @Command(name = "fs", description = "build a debian repository to/from local file system")
        @SneakyThrows
        public void run(
                @Option(names = {"-i", "--input", "--input-dir", "--index-dir"}, description = "directory with index files (./$codename/**/*.deb)", required = true)
                Path indexDir,
                @Option(names = {"-o", "--output", "--output-dir"}, description = "output directory: 'dists' (for 'Release', 'main/binary-$arch/Packages')", required = true)
                Path outDir,
                @Option(names = {"-c", "--codename"}, description = "which codenames to process (or all, if none specified)")
                List<String> codenames
        ) {
            run(codenames, new BuildRepositoryIO.FileBrIo(JsonMapper.builder().findAndAddModules().build(), indexDir, outDir));
        }

        @Command(name = "s3", description = "build a debian repository to/from s3")
        public void s3(
                @Option(names = {"-r", "--region"}, description = "aws bucket region (currently must be same for both)", required = true)
                String region,
                @Option(names = {"-i", "--s3-in-url"}, description = "input directory (for 'pool', 'dists', 'Release', 'main/binary-$arch/Packages')", required = true)
                URI s3InUrl,
                @Option(names = {"-o", "--s3-out-url"}, description = "output directory (for 'pool', 'dists', 'Release', 'main/binary-$arch/Packages')", required = true)
                URI s3OutUrl,
                @Option(names = {"-c", "--codename"}, description = "which codenames to process (or all, if none specified)")
                List<String> codenames
        ) {
            run(codenames,
                    new BuildRepositoryIO.S3BrIo(
                            S3Client.builder()
                                    .region(Region.of(region))
                                    .forcePathStyle(true)
                                    .build(),
                            JsonMapper.builder().findAndAddModules().build(),
                            s3InUrl,
                            s3OutUrl)
            );
        }

        private void run(List<String> codenames, BuildRepositoryIO bio) {
            var buildRepository = new BuildRepository();
            var repoBuilder = buildRepository.repoBuilder();

            var builders = bio.readMetas();
            List<String> codeNamesFiltered = determineCodenames(codenames, new ArrayList<>(builders.keySet()));
            for (String codename : codeNamesFiltered) {
                var builder = repoBuilder.buildCodeName(codename);
                builders.getOrDefault(codename, List.of()).forEach(builder::addIndex);
                builder.build();
            }

            var repo = repoBuilder.build();
            var files = buildRepository.buildRepo(repo);
            bio.writeFiles(files);
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
