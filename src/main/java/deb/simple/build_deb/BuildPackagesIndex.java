package deb.simple.build_deb;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class BuildPackagesIndex {
    final String poolPath;

    public BuildPackagesIndex(String poolPath) {
        this.poolPath = StringUtils.strip(poolPath, "/");
    }

    public String buildPackagesIndex(List<DebPackageMeta> debPackageMetaList) {
        return debPackageMetaList.stream()
                .map(this::metaToIndex)
                .collect(Collectors.joining("\n"));
    }

    public String metaToIndex(DebPackageMeta debPackageMeta) {
        DebPackageConfig config = debPackageMeta.getDebPackageConfig();
        DebPackageConfig.PackageMeta meta = config.getMeta();
        DebPackageConfig.ControlExtras control = config.getControl();

        var sb = new StringBuilder();
        sb.append("Package: ").append(meta.getName()).append("\n");
        sb.append("Version: ").append(meta.getVersion()).append("\n");
        sb.append("Architecture: ").append(meta.getArch()).append("\n");
        sb.append("Maintainer: ").append(control.getMaintainer()).append("\n");

        if (!control.getDepends().isBlank())
            sb.append("Depends: ").append(control.getDepends()).append("\n");
        if (!control.getConflicts().isBlank())
            sb.append("Conflicts: ").append(control.getConflicts()).append("\n");
        if (!control.getRecommends().isBlank())
            sb.append("Recommends: ").append(control.getRecommends()).append("\n");

        sb.append("Filename: ")
                .append("pool/").append(poolPath).append("/")
                .append(meta.getDebFilename()).append("\n");
        if (debPackageMeta.getDebPackageConfig().getControl().getInstalledSize() != null)
            sb.append("Installed-Size: ").append(debPackageMeta.getDebPackageConfig().getControl().getInstalledSize()).append("\n");
        sb.append("Size: ").append(debPackageMeta.getSize()).append("\n");
        sb.append("MD5sum: ").append(debPackageMeta.getHashes().getMd5()).append("\n");
        sb.append("SHA1: ").append(debPackageMeta.getHashes().getSha1()).append("\n");
        sb.append("SHA256: ").append(debPackageMeta.getHashes().getSha256()).append("\n");
        sb.append("SHA512: ").append(debPackageMeta.getHashes().getSha512()).append("\n");

        sb.append("Section: ").append(control.getSection()).append("\n");
        sb.append("Priority: ").append(control.getPriority()).append("\n");
        if (!control.getHomepage().isEmpty())
            sb.append("Homepage: ").append(control.getHomepage()).append("\n");
        sb.append("Description: ").append(control.getDescription()).append("\n");

        return sb.toString();
    }
}
