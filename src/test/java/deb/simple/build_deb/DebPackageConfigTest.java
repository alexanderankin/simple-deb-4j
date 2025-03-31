package deb.simple.build_deb;

import com.fasterxml.jackson.databind.ObjectMapper;
import deb.simple.build_deb.DebPackageConfig.TarFileSpec.BinaryTarFileSpec;
import deb.simple.build_deb.DebPackageConfig.TarFileSpec.TextTarFileSpec;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class DebPackageConfigTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    void test_deserialization() {
        var result = objectMapper.readValue("""
                {
                    "controlFiles": [{"type": "text", "content": "echo hi", "path": "/usr/bin/hi", "mode": 1877}],
                    "dataFiles": [{"type": "binary", "content": "aGVsbG8=", "comment": "hello", "path": "/etc/hello"}]
                }
                """, DebPackageConfig.DebFileSpec.class);
        assertThat(result.getControlFiles().getFirst(), is(instanceOf(TextTarFileSpec.class)));
        assertThat(result.getDataFiles().getFirst(), is(instanceOf(BinaryTarFileSpec.class)));
        assertThat(((BinaryTarFileSpec) result.getDataFiles().getFirst()).getContent(), is(new byte[]{104, 101, 108, 108, 111}));
        assertThat(objectMapper.writer().withDefaultPrettyPrinter().writeValueAsString(result), containsString("aGVsbG8="));
    }

}
