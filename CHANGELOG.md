# Changelog for Snow

This document details changes between each release.

See: [Keep a Changelog](https://keepachangelog.com)

## [0.15.0]

### Added
* Added an "Internal APIs" section to the README. This mentions useful classes
  and methods for use in one's own projects.
* The test suite runner can now optionally dump all the errors and annotations.
* Added a new `Locator` class that encapsulates instance and schema locations.
  `Annotation` and `Error` use this.
* New linter rules:
  1. Added a check for case-insensitive matching for unknown keywords.
  2. Added implied type checks for "default" and "const".

### Changed
* Updated to the latest Draft 2019-09 schemas.
* Changed the schema resource hierarchy so that it matches the JSON Schema
  upstream hierarchy.
* Restructured annotations and errors. `Annotation` was split into the
  parameterized `Annotation` and `Error` classes. `Error` replaces the
  `ValidationResult` `Annotation` value.
* Changed the linter to print URI fragments instead of paths.

## [0.14.0]

### Added
* First Maven Central Repository release.
* Added a `JSON.getStringMember` convenience method that can get the value of a
  string-valued object member. It can easily access the title, for example.

### Changed
* Changed the URL and Pattern caches to allow for a non-restricted size. They
  both remain associated with a specific `ValidatorContext` instance and are not
  shared across instances.
* Improved Gson's misleading syntax error messages. They refer to setting
  lenient mode to true, even if that's not what is wanted and if setting it to
  true wouldn't help anyway.
* Changed the $ref and $recursiveRef error message for target schema failures to
  the resolved URI of the target schema.
* Added sorting to both basic and annotation output in `Main`. The output is
  sorted by instance location and then by schema location.

### Fixed
* Fixed error pruning for passing schemas to restrict by schema location.
* Added a missing CONTENT option section to the README.
* Fixed `CoreRecursiveRef` to use the correct absolute location when applying
  the schema.

## [0.13.0]

### Added
* Added a `Validator` constructor for storing information across schema
  validation for the same schema.
* Added `URI.encodeFragment` for easy conversion from JSON Pointers to
  URI fragments.
* Added `JSONPath.toURIFragmentID` to return the URI fragment identifier form.

### Changed
* Annotation and error collection is now specified by passing `null` or
  non-`null` maps instead of the options.
* Empty annotation elements are now removed before returning from
  `ValidatorContext.apply`.
* Changed all "https://tools.ietf.org/html/rfcXXXX" URLs to
  "https://www.rfc-editor.org/rfc/rfcXXXX.html" because that site is more
  responsive. Also ensured the ".html" suffix is there consistently.

### Removed
* The `COLLECT_ANNOTATIONS` and `COLLECT_ERRORS` options. Collection of these is
  now specified depending on whether a map is passed to the API to hold them.

### Fixed
* Annotations are now always collected in keywords that collect them instead of
  being skipped if the keyword does not validate.
* Errors and annotations are now properly pruned, but at the schema level.
* Fixed the starting "absoluteKeywordLocation" to be the root ID if it exists,
  otherwise it remains the base URI.

## [0.12.0]

### Added
* Added the ability to add pluggable rules to the linter.
* Added non-schema tree traversal to `JSON`.
* `JSONPath.endsWith` for checking the last element.
* New linter rule that checks for `$ref` elements having siblings, for Draft-07
  and earlier.
* New `Id.element` field that contains the ID's JSON parent element.
* New `Id.unresolvedID` field that holds the unresolved ID URI.
* ID scanning and validation is now performed on all known IDs and URLs before
  validation so that all references (to valid IDs) will resolve.
* Added some total load and run times to the test runner.
* All known schemas, including from URLs, are now validated.

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
* The Coverage tool now outputs two JSON objects: coverage by instance location
  and the seen-only schema locations.
* Updated how `JSON.traverseSchema` works to be more complete. It now provides
  a more complete picture of the schema and the state of its members. This also
  means that all the keyword detection logic is in one place.
* Updated the linter and coverage tool to utilize the new schema
  traverser features.
* `Options.set` now returns itself, for easy chaining.
* The "ipv4" and "ipv6" formats are now parsed using the internal URI parser.
  That uses a more current specification.
* Changed `LRUCache` maximum size to be changeable.

### Fixed
* AUTO_RESOLVE now behaves properly for relative IDs.

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
