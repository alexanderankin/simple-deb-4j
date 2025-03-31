package deb.simple.build_deb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import deb.simple.build_deb.DebPackageConfig.TarFileSpec.BinaryTarFileSpec;
import deb.simple.build_deb.DebPackageConfig.TarFileSpec.TextTarFileSpec;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class DebPackageConfigTest {

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    YAMLMapper yamlMapper = (YAMLMapper) new YAMLMapper().findAndRegisterModules();

    @SneakyThrows
    @Test
    void test_deserializationFileSpecSubTypes() {
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

    @SneakyThrows
    @Test
    void test_yamlDeserialization() {
        var result = yamlMapper.readValue("""
                meta:
                  name: test
                  version: 0.0.1
                  arch: amd64
                files:
                  controlFiles: []
                  dataFiles:
                    - type: text
                      content: "blah"
                      path: "/etc/example"
                control:
                  depends: ""
                  recommends: ""
                  section: "main"
                  priority: "optional"
                  homepage: ""
                  maintainer: "maintainer"
                  description: "description"
                """, DebPackageConfig.class);
    }

}
