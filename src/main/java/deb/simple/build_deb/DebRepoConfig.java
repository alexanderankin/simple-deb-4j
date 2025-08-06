package deb.simple.build_deb;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DebRepoConfig {
    String origin;
    String label;
}
