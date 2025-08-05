package deb.simple.build_deb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;

@Data
@Accessors(chain = true)
public class FileIntegrity {
    @JsonIgnore
    byte[] content;
    String path;
    int size;
    String md5;
    String sha1;
    String sha256;
    String sha512;

    public static FileIntegrity of(String content, String path) {
        return FileIntegrity.of(content.getBytes(StandardCharsets.UTF_8), path);
    }

    public static FileIntegrity of(byte[] content, String path) {
        return new FileIntegrity()
                .setContent(content)
                .setPath(path)
                .setSize(content.length)
                .setMd5(DigestUtils.md5Hex(content))
                .setSha1(DigestUtils.sha1Hex(content))
                .setSha256(DigestUtils.sha256Hex(content))
                .setSha512(DigestUtils.sha512Hex(content))
                ;
    }
}
