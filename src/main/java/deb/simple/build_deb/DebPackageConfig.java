package deb.simple.build_deb;

import com.fasterxml.jackson.annotation.*;
import deb.simple.DebArch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebPackageConfig {
    @NotNull
    @Valid
    PackageMeta meta;
    @NotNull
    @Valid
    ControlExtras control;
    @NotNull
    @Valid
    DebFileSpec files;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TarFileSpec.TextTarFileSpec.class, name = "text"),
            @JsonSubTypes.Type(value = TarFileSpec.BinaryTarFileSpec.class, name = "binary"),
            @JsonSubTypes.Type(value = TarFileSpec.FileTarFileSpec.class, name = "file"),
            @JsonSubTypes.Type(value = TarFileSpec.DirTarFileSpec.class, name = "dir"),
            @JsonSubTypes.Type(value = TarFileSpec.UrlTarFileSpec.class, name = "url"),
            @JsonSubTypes.Type(value = TarFileSpec.S3ObjectTarFileSpec.class, name = "s3"),
            @JsonSubTypes.Type(value = TarFileSpec.S3ZipArchiveTarFileSpec.class, name = "s3zip"),
    })
    @Data
    @Accessors(chain = true)
    public static sealed abstract class TarFileSpec {
        @NotBlank
        String path;
        Integer mode;

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class TextTarFileSpec extends TarFileSpec {
            @NotBlank
            String content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class BinaryTarFileSpec extends TarFileSpec {
            @NotEmpty
            byte[] content;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class FileTarFileSpec extends TarFileSpec {
            @NotBlank
            String sourcePath;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class DirTarFileSpec extends TarFileSpec {
            @NotBlank
            String sourcePath;

            @NotNull
            ModeMode modeMode = ModeMode.INHERIT;

            Map<String, Integer> modeOverrides;

            public enum ModeMode {
                INHERIT, OVERRIDE
            }
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class UrlTarFileSpec extends TarFileSpec {
            @NotNull
            URI url;
            String bearerToken;
            LinkedHashMap<String, List<String>> headers;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static sealed class S3ObjectTarFileSpec extends TarFileSpec {
            @NotNull
            URI s3Url;
            String region;
        }

        @ToString(callSuper = true)
        @EqualsAndHashCode(callSuper = true)
        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class S3ZipArchiveTarFileSpec extends S3ObjectTarFileSpec {
            @NotNull
            String zipPath;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageMeta {
        public static final String SD_INDEX_EXTENSION = ".simple-deb-4j-index.json";
        @NotBlank
        String name;
        @NotBlank
        String version;
        @NotNull
        DebArch arch;

        @JsonIgnore
        public String getDebFilename() {
            return name + "_" + version + "_" + arch + ".deb";
        }

        @JsonIgnore
        public String getIndexFilename() {
            return name + "_" + version + "_" + arch + SD_INDEX_EXTENSION;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ControlExtras {
        @NotNull
        @JsonAlias("Depends")
        String depends = "";
        @NotNull
        @JsonAlias("Recommends")
        String recommends = "";
        @NotBlank
        @JsonAlias("Section")
        String section = "main";
        @NotBlank
        @JsonAlias("Priority")
        String priority = "optional";
        @NotNull
        @JsonAlias("Homepage")
        String homepage = "";
        @NotNull
        @JsonAlias("Conflicts")
        String conflicts = "";
        @JsonAlias("InstalledSize")
        Integer installedSize;
        @NotBlank
        @JsonAlias("Maintainer")
        String maintainer = "";
        @NotBlank
        @JsonAlias("Description")
        String description = "";

        public ControlExtras setSection(String section) {
            this.section = StringUtils.isBlank(section) ? "main" : section;
            return this;
        }

        public ControlExtras setPriority(String priority) {
            this.priority = StringUtils.isBlank(priority) ? "optional" : priority;
            return this;
        }

        public String render(PackageMeta meta) {
            return String.format("""
                            Package: %s
                            Version: %s
                            Depends: %s
                            Recommends: %s
                            Section: %s
                            Priority: %s
                            Homepage: %s
                            Conflicts: %s
                            Architecture: %s
                            Installed-Size: %d
                            Maintainer: %s
                            Description: %s
                            """,
                    // optional fields
                    meta.getName(), meta.getVersion(), depends, recommends, section, priority,
                    homepage, conflicts, meta.getArch(), installedSize,
                    // required fields
                    maintainer, description
            ).strip() + "\n";
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DebFileSpec {
        @NotNull
        List<@Valid TarFileSpec> controlFiles;
        @NotNull
        List<@Valid TarFileSpec> dataFiles;
    }
}
