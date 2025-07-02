package deb.simple.build_deb;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DebPackageMeta {
    DebPackageConfig debPackageConfig;
    FileIntegrity hashes;
    Integer size;
}
