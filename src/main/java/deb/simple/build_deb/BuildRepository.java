package deb.simple.build_deb;

import deb.simple.DebArch;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * a "repo" is:
 * <ol>
 *     <li>a dists folder inside a base directory</li>
 *     <li>the codename folders inside dists</li>
 *     <li>the Release, Release.gpg, InRelease files inside the codename folder</li>
 *     <li>the main/binary-$arch/Packages{,.gz} files inside the codename folder</li>
 * </ol>
 * this class will build a {@link Map} with those files and their contents
 */
public class BuildRepository {
    /**
     * this method gives you a tool to collect repo information, i.e. a {@link Repo} instance
     *
     * @return a {@link Repo} builder
     */
    public RepoBuilder repoBuilder() {
        return new RepoBuilder();
    }

    public RepoBuilder repoBuilder(Instant now) {
        return new RepoBuilder(now);
    }

    /**
     * returns all the files you need to make the repo
     *
     * @param repo information about the repo
     * @return all files needed for apt to update from repo
     */
    public Map<String, FileIntegrity> buildRepo(Repo repo) {
        return repo.codenameSectionMap().entrySet().stream()
                .flatMap(e ->
                        e.getValue().packagesFiles().entrySet().stream()
                                .map(ee ->
                                        Map.entry(e.getValue().getCodename() + "/" + ee.getKey(), ee.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // <T> Stream<T> streamFromIterator(Iterator<T> i) {
    //     return StreamSupport.stream(Spliterators.spliteratorUnknownSize(i, 0), false);
    // }
    //
    // @SneakyThrows
    // public void signFiles(Map<String, FileIntegrity> files, String signingKey, String signingPublicKey) {
    //     var releaseFiles = files.entrySet().stream()
    //             .filter(e -> e.getKey().endsWith("Release"))
    //             .toList();
    //
    //     // PGPSecretKeyRing secretKey = PGPainless.readKeyRing().secretKeyRing(signingKey);
    //     // if (secretKey == null) {
    //     //     throw new RuntimeException("No secret key found for signing");
    //     // }
    //     //
    //     // SecretKeyRingProtector protector = SecretKeyRingProtector.unprotectedKeys();
    //     //
    //     // // Get the first user ID (email) from the secret key
    //     // String userId = streamFromIterator(secretKey.getSecretKeys())
    //     //         .flatMap(k -> streamFromIterator(k.getUserIDs()))
    //     //         .findFirst()
    //     //         .orElseThrow(() -> new RuntimeException("No user ID (email) found in secret key"));
    //
    //     for (Map.Entry<String, FileIntegrity> releaseFile : releaseFiles) {
    //         String releasePath = releaseFile.getKey();
    //         String releasePathPrefix = releasePath.substring(0, releasePath.length() - "Release".length());
    //
    //         // 1. Detached ASCII-armored signature: Release.gpg
    //         String sigPath = releasePath + ".gpg";
    //
    //         // try (ByteArrayOutputStream out = new ByteArrayOutputStream();
    //         //      OutputStream armored = new ArmoredOutputStream(out)) {
    //         //
    //         //     SigningOptions options = SigningOptions.get()
    //         //             .addDetachedSignature(protector, secretKey, userId);
    //         //
    //         //     try (OutputStream signer = PGPainless.encryptAndOrSign()
    //         //             .onOutputStream(armored)
    //         //             .withOptions(ProducerOptions.sign(options)
    //         //                     .setCleartextSigned())) {
    //         //
    //         //         new ByteArrayInputStream(releaseFile.getValue().getContent())
    //         //                 .transferTo(signer);
    //         //     }
    //         //
    //         //     System.out.println(out.toString(StandardCharsets.UTF_8));
    //         //     files.put(sigPath, FileIntegrity.of(out.toByteArray(), sigPath));
    //         // }
    //
    //
    //         // 2. Clear-signed file: InRelease
    //         String inReleasePath = releasePathPrefix + "InRelease";
    //
    //         // try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
    //         //     SigningOptions options = SigningOptions.get()
    //         //             .addInlineSignature(protector, secretKey, userId);
    //         //
    //         //     try (OutputStream signer = PGPainless.encryptAndOrSign()
    //         //             .onOutputStream(out)
    //         //             .withOptions(ProducerOptions.sign(options))) {
    //         //
    //         //         new ByteArrayInputStream(releaseFile.getValue().getContent())
    //         //                 .transferTo(signer);
    //         //     }
    //         //
    //         //     files.put(inReleasePath, FileIntegrity.of(out.toByteArray(), inReleasePath));
    //         // }
    //
    //         var signed = BuildRepositoryGpgSignatures.signReleaseFile(signingKey, signingPublicKey, releaseFile.getValue().getContent());
    //         files.put(sigPath, FileIntegrity.of(signed.get("Release.gpg"), sigPath));
    //         files.put(inReleasePath, FileIntegrity.of(signed.get("InRelease"), inReleasePath));
    //
    //         String repositoryGpgPath = releasePathPrefix + "repository.gpg";
    //         files.put(repositoryGpgPath, FileIntegrity.of(signed.get("repository.gpg"), repositoryGpgPath));
    //     }
    // }

    @Data
    @Accessors(chain = true)
    public static class Repo {
        Map<String, CodenameSection> codenameSectionMap;

        public Map<String, CodenameSection> codenameSectionMap() {
            if (codenameSectionMap == null) {
                codenameSectionMap = new HashMap<>();
            }
            return codenameSectionMap;
        }

        @Data
        @Accessors(chain = true)
        public static class CodenameSection {
            final String codename;
            Set<DebArch> arches;
            Set<String> components;
            Instant date;
            Map<String, FileIntegrity> packagesFiles;

            Set<DebArch> arches() {
                if (arches == null) {
                    arches = new HashSet<>();
                }
                return arches;
            }

            Set<String> components() {
                if (components == null) {
                    components = new HashSet<>();
                }
                return components;
            }

            Map<String, FileIntegrity> packagesFiles() {
                if (packagesFiles == null) {
                    packagesFiles = new HashMap<>();
                }
                return packagesFiles;
            }

            public String getDefaultDescription() {
                return "Repository for " + codename;
            }
        }
    }

    public static class RepoBuilder {
        final Repo repo = new Repo();
        final Instant now;

        RepoBuilder() {
            this(Instant.now());
        }

        RepoBuilder(Instant now) {
            this.now = now;
        }

        public Repo build() {
            return repo;
        }

        public CodenameSectionBuilder buildCodeName(String codename) {
            Repo.CodenameSection codenameSection = repo.codenameSectionMap().computeIfAbsent(codename, Repo.CodenameSection::new);
            codenameSection.setDate(now);
            return new CodenameSectionBuilder(this, codenameSection);
        }

        @RequiredArgsConstructor
        public static class CodenameSectionBuilder {
            @NonNull
            final RepoBuilder repoBuilder;
            @NonNull
            final Repo.CodenameSection codenameSection;
            final List<DebPackageMeta> debPackageMetaList = new ArrayList<>();

            public RepoBuilder build() {
                var packagesFiles = codenameSection.packagesFiles();

                var bpi = new BuildPackagesIndex(codenameSection.getCodename());

                for (String component : codenameSection.components()) {
                    for (DebArch arch : codenameSection.arches()) {
                        var packagesFile = component + "/binary-" + arch + "/Packages";
                        var packagesListForArch = debPackageMetaList.stream()
                                .filter(m -> m.getDebPackageConfig().getMeta().getArch() == arch)
                                .toList();
                        var content = bpi.buildPackagesIndex(packagesListForArch);
                        var integrity = FileIntegrity.of(content, packagesFile);
                        packagesFiles.put(packagesFile, integrity);

                        var packagesFileGz = packagesFile + ".gz";
                        var integrityGz = FileIntegrity.of(gzip(content), packagesFileGz);
                        packagesFiles.put(packagesFileGz, integrityGz);
                    }
                }

                var br = new BuildRelease();
                br.buildReleaseToString(codenameSection);

                return repoBuilder;
            }

            @SneakyThrows
            byte[] gzip(String content) {
                var o = new ByteArrayOutputStream();
                try (var i = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                     var g = new GZIPOutputStream(o)) {
                    i.transferTo(g);
                }
                return o.toByteArray();
            }

            public CodenameSectionBuilder addIndex(DebPackageMeta meta) {
                codenameSection.arches().add(meta.getDebPackageConfig().getMeta().getArch());
                codenameSection.components().add(meta.getDebPackageConfig().getControl().getSection());
                debPackageMetaList.add(meta);
                return this;
            }
        }
    }
}
