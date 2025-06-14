# simple-deb-4j

This project provides a library and command line tool for performing the debian package publishing steps.

## changes

* version 0.0.6
  * add ability to also create `.deb.simple-deb-4j-index` files to reduce indexing IO
    * these files are like the `.deb` except excluding the `data.tar.gz`
    * you can download these files, rename them to `.deb` and index as with the full files

* version 0.0.5 - `Wed, 14 May 2025 12:17:23 -0400`
  * add `-C` flag to build command
  * conflicts support (in dto, control file template, and JSON schema for editing the config files)

* version 0.0.4 - `Fri, 04 Apr 2025 10:13:05 -0400`
  * fix schema for `file` file type (require `sourcePath` which exists, not `file` which does not)
  * add tests and use JimFs for file type test
  * use `Path#resolve` not `Path#of`: library can support virtual file systems
  * add function to generate gpg key `simple-deb g gk "name" "email@example.com" -P public.gpg -K private.gpg`

* version 0.0.3 - `Mon, 31 Mar 2025 23:39:30 -0400`
  * support `file` and `url` types of files
  * arch is an enum with two members for `amd64` and `arm64`

* version 0.0.2 - `Mon, 31 Mar 2025 14:15:32 -0400`
  * fix cli bug while deserializing config file
  * YAML support
  * change upload pattern
  * include published json schema for IDE

* version 0.0.1 - `Mon, 31 Mar 2025 12:29:56 -0400`
  * builds a debian package that installs files (data files and `postinst`, etc...)
