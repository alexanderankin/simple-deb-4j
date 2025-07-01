package deb.simple.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import deb.simple.DebArch;
import deb.simple.build_deb.*;
import deb.simple.gpg.GenerateGpgKey;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.bouncycastle.util.io.TeeOutputStream;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static deb.simple.build_deb.DebPackageConfig.PackageMeta.SD_INDEX_EXTENSION;

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
        args = new String[]{"b", "-c", "/home/toor/IdeaProjects/simple-deb-4j/src/test/resources/deb/simple/build_deb/simple.yaml", "-o", "/tmp/tmp.MTtdYc3eNo", "-i"};
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

    @Command(name = "repo", aliases = {"r"}, description = "build a debian repository")
    static class BuildRepo implements Runnable {
        ObjectMapper o = new ObjectMapper().findAndRegisterModules();

        @Option(names = {"-i", "--input", "--input-dir", "--index-dir"}, description = "directory with index files (./$codename/**/*.deb")
        Path indexDir;

        @Option(names = {"-o", "--output", "--output-dir"}, description = "output directory: 'dists' (for 'Release', 'main/binary-$arch/Packages')")
        Path outDir;

        @Option(names = {"-c", "--codename"}, description = "which codenames to process (or all, if none specified)")
        List<String> codenames;

        @SneakyThrows
        @Override
        public void run() {
            List<String> codeNamesFiltered = determineCodenames();
            for (String codename : codeNamesFiltered) {
                var builder = new BuildPackagesIndex(codename).builder();
                try (var files = Files.walk(indexDir.resolve(codename))) {
                    files
                            .filter(file -> file.getFileName().toString().endsWith(SD_INDEX_EXTENSION))
                            .map(this::readValue)
                            .forEach(builder::add);
                }

                Map<DebArch, String> packagesFilesByArch = builder.buildByArch();
                Map<String, FileIntegrity> packagesFilesIntegrity = new HashMap<>();
                for (var entry : packagesFilesByArch.entrySet()) {
                    DebArch arch = entry.getKey();
                    String packagesContent = entry.getValue();

                    Path relative = Path.of(codename, "main", "binary-" + arch, "Packages");
                    String relativePathString = relative.toString();
                    Path outFile = outDir.resolve(relative);

                    // noinspection ResultOfMethodCallIgnored
                    outFile.getParent().toFile().mkdirs();
                    Files.writeString(outFile, packagesContent);
                    packagesFilesIntegrity.put(relativePathString, FileIntegrity.of(packagesContent, relativePathString));

                    // also produce Packages.gz
                    String relativePathStringGz = GzipUtils.getCompressedFileName(relativePathString);

                    ByteArrayOutputStream packagesGzContents = new ByteArrayOutputStream();
                    try (var in = Files.newInputStream(outFile);
                         var t = new TeeInputStream(in, packagesGzContents, true);
                         var out = new GzipCompressorOutputStream(new FileOutputStream(outDir.resolve(relativePathStringGz).toString()))) {
                        t.transferTo(out);
                    }

                    packagesFilesIntegrity.put(relativePathStringGz, FileIntegrity.of(packagesGzContents.toByteArray(), relativePathStringGz));
                }

                String releaseContent = new BuildRelease()
                        .buildReleaseToString(new BuildRelease.RepoRelease()
                                .setCodename(builder.codename())
                                .setNow(Instant.now())
                                .setArches(builder.arches())
                                .setComponents(builder.components())
                                .setPackagesHashes(packagesFilesIntegrity)
                        );

                Files.writeString(outDir.resolve(Path.of(codename, "Release")), releaseContent);
            }
        }

        private List<String> determineCodenames() {
            List<String> codeNamesFound = readCodeNames();
            List<String> codeNamesFiltered = filterCodeNames(codeNamesFound);
            log.info("using codename list: {} in directory {} based on filter list: {}", codeNamesFiltered, indexDir, codenames);
            if (codenames != null && codeNamesFound.size() != codenames.size())
                log.warn("codenames were specified and filtered down to {} from {}", codeNamesFiltered, codeNamesFound);
            return codeNamesFiltered;
        }

        private List<String> readCodeNames() {
            return Arrays.stream(Objects.requireNonNull(
                            indexDir.toFile().list(),
                            () -> "directory " + indexDir + " has no contents"
                    ))
                    .sorted()
                    .toList();
        }

        private List<String> filterCodeNames(List<String> codeNamesFound) {
            if (codenames == null || codenames.isEmpty()) {
                return codeNamesFound;
            } else {
                return codeNamesFound.stream().filter(codenames::contains).toList();
            }
        }

        @SneakyThrows
        DebPackageMeta readValue(Path i) {
            return o.readValue(i.toFile(), DebPackageMeta.class);
        }

        @Command(name = "s3", description = "build a debian repository to/from s3")
        public void s3(
                @Option(names = {"--s3-url"}, description = "input/output directory (for 'pool', 'dists', 'Release', 'main/binary-$arch/Packages')")
                URI s3Url
        ) {
        }

        @Command(name = "s3-repo", description = "build a debian repository to/from s3")
        public void s3Repo(
                @Option(names = {"-i", "--index-dir"}, description = "directory with index files ('pool')")
                Path indexDir,
                @Option(names = {"--s3-url"}, description = "output directory (for 'dists', 'Release', 'main/binary-$arch/Packages')")
                URI s3Url
        ) {
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
