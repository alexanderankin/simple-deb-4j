# simple-deb-4j

This project provides a library and command line tool for performing the debian package publishing steps.

## changes

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
