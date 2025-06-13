package deb.simple.cli;

import picocli.CommandLine;

class ManifestVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[]{getClass().getPackage().getImplementationVersion()};
    }
}
