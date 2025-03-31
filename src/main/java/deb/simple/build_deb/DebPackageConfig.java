package deb.simple.build_deb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

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
    })
    sealed interface TarFileSpec {
        String getPath();

        Integer getMode();

        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        final class TextTarFileSpec implements TarFileSpec {
            String path;
            String content;
            Integer mode;
        }

        @Data
        @Accessors(chain = true)
        @JsonIgnoreProperties(ignoreUnknown = true)
        final class BinaryTarFileSpec implements TarFileSpec {
            String path;
            byte[] content;
            Integer mode;
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
        /**
         * todo make me an enum as reported by `dpkg-architecture -L --match-bits 64 --match-endian little --match-wildcard linux-any`
         */
        @NotBlank
        String arch;

        public String getDebFilename() {
            return name + "_" + version + "_" + arch + ".deb";
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
                            Architecture: %s
                            Installed-Size: 10
                            Maintainer: %s
                            Description: %s
                            """,
                    meta.getName(), meta.getVersion(), depends, recommends, section, priority,
                    homepage, meta.getArch(), maintainer, description
            ).strip() + "\n";
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DebFileSpec {
        List<TarFileSpec> controlFiles;
        List<TarFileSpec> dataFiles;
    }
}
