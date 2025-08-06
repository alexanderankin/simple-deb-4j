# refactoring

The `Build*` classes are supposed to represent composable operations.
Some do this, others must be fixed

| `Build*` class       | description                                        | status     |
|----------------------|----------------------------------------------------|------------|
| `BuildDeb`           | turns `DebPackageConfig` into `*.deb`              | does that  |
| `BuildIndex`         | turns `DebPackageConfig` and `*.deb` into `*.json` | does that  |
| `BuildPackagesIndex` | turns `List<*.json>` into `Packages`               | does that  |
| `BuildRelease`       | helper for BuildRepository relating to `Release`   | needs help |
| `BuildRepository`    | builder accumulates `*.json` to map of files       | needs help |
| `BuildRepositoryIO`  | abstraction for `BuildRepository` for I/O (s3, fs) | does that  |

`DebPackageConfig` is the input for building deb files: this works well.

`DebPackageMeta` is the DebPackageConfig + `FileIntegrity` (hashes) of the output deb file: this works well.

## todo

improve:

* `BuildRelease`
* `BuildRepository`
