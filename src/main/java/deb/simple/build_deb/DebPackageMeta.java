package deb.simple.build_deb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DebPackageMeta {
    DebPackageConfig debPackageConfig;
    Hashes hashes;
    Integer size;

    @Data
    @Accessors(chain = true)
    public static class Hashes {
        @JsonProperty("MD5sum")
        String md5sum;
        @JsonProperty("SHA1")
        String sha1;
        @JsonProperty("SHA256")
        String sha256;
        @JsonProperty("SHA512")
        String sha512;
    }
}
