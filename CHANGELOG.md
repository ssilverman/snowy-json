# Changelog for Snow

This document details changes between each release.

See: [Keep a Changelog](https://keepachangelog.com)

## [0.8.0]

### Added
* More `idn-hostname` format checks.
* URL support in `Main` and `Linter`.
* Internal $ref existence checking in `Linter`.
* Table of contents in the README.
* New "The linter" section in the README.

### Fixed

* There's now correct use of "an" vs. "a" in the error message in `Type`.
* Annotation prefix checking now considers the final "/" and doesn't just match
  the prefix.

## [0.7.1]

### Changed
* Changed the Maven artifactId to "snowy-json" from "json-schema".

## [0.7.0]

### Added
* New section on annotations and errors in the README.
* New annotation type descriptions in the README.
* Optional test counts in the test runner.
* New AUTO_RESOLVE option.
* New `Options.is` method for reading Boolean values more conveniently.

### Changed
* Better and more consistent internal handling of anchors and IDs.

### Removed
* Annotation collection was removed from the Contains keyword.

### Fixed
* Fixed percent encoder, used when resolving absolute locations.
* Relative paths resolved against a base URI having an empty path no longer
  result in an infinite loop.
* Other code and Javadoc fixes.

## [0.6.0]

Initial public release.

---
Copyright (c) 2020 Shawn Silverman