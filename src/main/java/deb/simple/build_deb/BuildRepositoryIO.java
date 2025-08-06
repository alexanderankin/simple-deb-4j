package deb.simple.build_deb;

import com.fasterxml.jackson.databind.ObjectMapper;
import deb.simple.build_deb.BuildRepository.Repo;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static deb.simple.build_deb.DebPackageConfig.PackageMeta.SD_INDEX_EXTENSION;

/**
 * reading and writing {@link Repo}
 */
public interface BuildRepositoryIO {
    Map<String, List<DebPackageMeta>> readMetas();

    void writeFiles(Map<String, FileIntegrity> files);

    // ObjectMapper getObjectMapper();

    @Slf4j
    @Data
    @Accessors(chain = true)
    class FileBrIo implements BuildRepositoryIO {
        @NonNull
        ObjectMapper objectMapper;
        @NonNull
        Path inDir;
        @NonNull
        Path outDir;

        @SneakyThrows
        @Override
        public Map<String, List<DebPackageMeta>> readMetas() {
            var result = new HashMap<String, List<DebPackageMeta>>();
            for (String codeName : Objects.requireNonNull(inDir.toFile().list())) {
                try (var files = Files.walk(inDir.resolve(codeName))) {
                    var list = files
                            .filter(file -> file.getFileName().toString().endsWith(SD_INDEX_EXTENSION))
                            .map(this::readValue)
                            .toList();
                    result.computeIfAbsent(codeName, ignored -> new ArrayList<>()).addAll(list);
                }
            }


            return result;
        }

        @SneakyThrows
        private DebPackageMeta readValue(Path path) {
            return objectMapper.readValue(path.toFile(), DebPackageMeta.class);
        }

        @SneakyThrows
        @Override
        public void writeFiles(Map<String, FileIntegrity> files) {
            for (var fileEntry : files.entrySet()) {
                log.info("writing file {} relative to dir {}", fileEntry.getKey(), outDir);
                Path target = outDir.resolve(fileEntry.getKey());
                FileUtils.createParentDirectories(target.toFile());
                Files.write(target, fileEntry.getValue().getContent());
            }
        }
    }

    @Slf4j
    @Data
    @Accessors(chain = true)
    class S3BrIo implements BuildRepositoryIO {
        @NonNull
        S3Client s3Client;
        @NonNull
        ObjectMapper objectMapper;
        @NonNull
        URI inPrefix;
        @NonNull
        URI outPrefix;

        @SneakyThrows
        @Override
        public Map<String, List<DebPackageMeta>> readMetas() {
            var resultKeys = new HashMap<String, List<S3Object>>();

            var response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(inPrefix.getHost())
                    .prefix(StringUtils.stripStart(inPrefix.getPath(), "/"))
                    .build());

            addListToResultKeys(filter(response.contents()), resultKeys);

            if (response.isTruncated()) {
                String token = response.nextContinuationToken();
                response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(inPrefix.getHost())
                        .prefix(StringUtils.stripStart(inPrefix.getPath(), "/"))
                        .continuationToken(token)
                        .build());

                addListToResultKeys(filter(response.contents()), resultKeys);
            }

            return resultKeys.entrySet().stream()
                    .map(codeNameKeys -> {
                        var values = codeNameKeys.getValue().stream()
                                .parallel()
                                .map(s3Object -> getDebPackageMeta(s3Object, s3Client))
                                .toList();
                        return Map.entry(codeNameKeys.getKey(), values);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @SneakyThrows
        private DebPackageMeta getDebPackageMeta(S3Object s3Object, S3Client s3Client) {
            var object = s3Client.getObject(GetObjectRequest.builder().bucket(inPrefix.getHost()).key(s3Object.key()).build());
            var objectString = IOUtils.toString(object, StandardCharsets.UTF_8);
            return objectMapper.readValue(objectString, DebPackageMeta.class);
        }

        private void addListToResultKeys(List<S3Object> list, HashMap<String, List<S3Object>> resultKeys) {
            list.forEach(s3Object -> {
                var codeName = s3Object.key().substring(inPrefix.getPath().length()).split("/")[0];
                List<S3Object> keyList = resultKeys.computeIfAbsent(codeName, ignored -> new ArrayList<>());
                keyList.add(s3Object);
            });
        }

        private List<S3Object> filter(List<S3Object> contents) {
            return contents.stream().filter(e -> e.key().endsWith(SD_INDEX_EXTENSION)).toList();
        }

        @Override
        public void writeFiles(Map<String, FileIntegrity> files) {
            List<Result<PutObjectResponse, Exception>> list = files.entrySet()
                    .stream()
                    .parallel()
                    .map(fileEntry -> {
                        try {
                            return new Result<PutObjectResponse, Exception>(s3Client.putObject(
                                    PutObjectRequest.builder()
                                            .checksumSHA256(Base64.getEncoder().encodeToString(Hex.decode(fileEntry.getValue().getSha256())))
                                            .bucket(outPrefix.getHost())
                                            .key(StringUtils.strip(outPrefix.getPath(), "/") + "/" + fileEntry.getKey())
                                            .build(),
                                    RequestBody.fromBytes(fileEntry.getValue().getContent())), null);
                        } catch (S3Exception e) {
                            return new Result<PutObjectResponse, Exception>(null, e);
                        }
                    })
                    .toList();

            var listSuccess = list.stream().map(Result::success).toList();
            log.debug("uploaded files: {}", listSuccess);
            if (!listSuccess.stream().allMatch(Boolean::booleanValue)) {
                log.warn("uploaded files not all successful: {}", list);
            }
        }

        record Result<T, E extends Throwable>(T successValue, E exceptionValue) {
            boolean success() {
                return exceptionValue == null;
            }
        }
    }
}
