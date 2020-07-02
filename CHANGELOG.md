# Changelog for Snow

This document details changes between each release.

See: [Keep a Changelog](https://keepachangelog.com)

## [0.12.0]

### Added
* Added the ability to add pluggable rules to the linter.
* Added non-schema tree traversal to `JSON`.
* `JSONPath.endsWith` for checking the last element.

### Changed
* Changed a couple linter behaviours:
  1. A string won't be examined if its parent is a definitions object, in
     addition to just a "properties" object.
  2. Unknown non-root keywords will have their properties examined. This is
     desirable because both definitions and "properties" objects allow unknown
     keywords. Previously, no unknown keywords had their properties examined.
* Errors reverted to instance->schema order for the mapping.
* All the fields are now final or private in `Annotation` and `Id`.
* Other internal improvements.
* Changed `JSON.traverse` to `traverseSchema`, `JSON.JsonElementVisitor` to
  `JSON.SchemaVisitor`, and `JSON.TraverseState` to `JSON.SchemaTraverseState`.
* The Coverage tool now prints coverage by instance path and not schema path.
  It also now outputs in JSON.

## [0.11.0]

### Added
* Added a rudimentary schema coverage tool, `Coverage`.

### Changed
* Introduced a `JSONPath` class that replaces strings for path representation.
  One of the advantages of this approach is not having to worry about "/"
  characters in names, or JSON Pointer syntax.
* Other updates, optimizations, refactors, and improvements.
* Errors are now mapped in schema->instance order instead of instance->schema
  order. This is now the reverse of how annotations are mapped.

### Fixed
* `Strings.fromJSONPointerToken` now throws an exception if the token contains
  a "/" character.
* "error" annotations are now marked valid=true.
* Annotations are now being properly collected for `then` and `else`.
* Fixed `URI.toString()` and `toDecodedString()` by flipping their behaviour.
* Fixed an absolute keyword location tracking bug.

## [0.10.0]

### Added
* Added expected type checking to the linter. For example, `minimum` expects a
  type of "number" or "integer".
* Added an "exclusive minimum" >= "exclusive maximum" check to the linter.
* README updates: "Schema and instance coverage" future plans and
  "IDN Hostnames" link in the reference list.
* Added annotation printing to `Main`.
* Added an option, `Option.COLLECT_ANNOTATIONS_FOR_FAILED`, that controls
  whether annotation collection also retains annotations for schemas that
  failed validation.

### Removed
* The notes for `Option.COLLECT_ERRORS` stated that it may need to be `false` in
  the presence of any `$recursiveRef`s. This note has been removed.

## [0.9.0]

### Added
* Added the _source_ and _javadoc_ package plugins to the POM.
* A linter check for `$id` values that have an empty fragment, for Draft 2019-09
  and later.
* Added "minimum" keyword > "maximum" keyword checks to the linter.
* Added a custom linting rule example to the README.

### Changed
* The EMAIL regex in Format now disallows local parts starting with a dot,
  ending with a dot, or containing two consecutive dots.
* URI and hostname parsing is now down with local code in the
  `com.qindesign.net` package.
* New internal URI and hostname parsing and handling. There's a new
  `com.qindesign.net` package containing `URI` and `Hostname` classes. `URI`
  replaces `java.net.URI` and `Hostname` handles both regular and IDN
  hostname parsing.
* The linter now uses a list of strings for the path instead of a single JSON
  Pointer string.

### Fixed
* Improved the UUID format checker.
* Javadoc updates and fixes.

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
