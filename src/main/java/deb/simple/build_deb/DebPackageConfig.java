package deb.simple.build_deb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import deb.simple.DebArch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebPackageConfig {
    @Valid
    PackageMeta meta;
    @Valid
    ControlExtras control;
    @Valid
    DebFileSpec files;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TarFileSpec.TextTarFileSpec.class, name = "text"),
            @JsonSubTypes.Type(value = TarFileSpec.BinaryTarFileSpec.class, name = "binary"),
            @JsonSubTypes.Type(value = TarFileSpec.FileTarFileSpec.class, name = "file"),
            @JsonSubTypes.Type(value = TarFileSpec.UrlTarFileSpec.class, name = "url"),
    })
    @Data
    @Accessors(chain = true)
    public static sealed abstract class TarFileSpec {
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
        public static final class UrlTarFileSpec extends TarFileSpec {
            @NotNull
            URI url;
            String bearerToken;
            LinkedHashMap<String, List<String>> headers;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageMeta {
        @NotBlank
        String name;
        @NotBlank
        String version;
        @NotNull
        DebArch arch;

        public String getDebFilename(boolean indexFileName) {
            return name + "_" + version + "_" + arch + (indexFileName ? ".deb.simple-deb-4j-index" : ".deb");
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ControlExtras {
        @NotNull
        String depends = "";
        @NotNull
        String recommends = "";
        @NotNull
        String section = "main";
        @NotNull
        String priority = "optional";
        @NotNull
        String homepage = "";
        @NotNull
        String conflicts = "";
        @NotNull
        String breaks = "";
        @NotNull
        String replaces = "";
        @NotBlank
        String maintainer = "";
        @NotBlank
        String description = "";

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
                            Breaks: %s
                            Architecture: %s
                            MD5sum: %s
                            SHA1: %s
                            SHA256: %s
                            SHA512: %s
                            Maintainer: %s
                            Description: %s""",
                    // optional fields
                    meta.getName(), meta.getVersion(), depends, recommends, section, priority,
                    homepage, conflicts, breaks, meta.getArch(),
                    // required fields
                    maintainer, description
            ) + "\n";
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DebFileSpec {
        List<@Valid TarFileSpec> controlFiles;
        List<@Valid TarFileSpec> dataFiles;
    }
}
