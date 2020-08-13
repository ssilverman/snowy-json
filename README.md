# Snow, a JSON Schema Validator

Version: 0.14.0

The main goal of this project is to be a reference JSON Schema validator. While
it provides a few working applications, it's meant primarily as an API for
building your own toolset.

See: [JSON Schema](https://json-schema.org)

## Table of contents

1. [Features](#features)
   1. [Additional features](#additional-features)
2. [Quick start](#quick-start)
3. [Under the covers](#under-the-covers)
4. [Limitations](#limitations)
5. [Which specification is used for processing?](#which-specification-is-used-for-processing)
6. [Options for controlling behaviour](#options-for-controlling-behaviour)
   1. [AUTO_RESOLVE](#option-auto_resolve)
   2. [COLLECT_ANNOTATIONS_FOR_FAILED](#option-collect_annotations_for_failed)
   3. [CONTENT](#option-content)
   4. [DEFAULT_SPECIFICATION](#option-default_specification)
   5. [FORMAT](#option-format)
   6. [SPECIFICATION](#option-specification)
7. [Project structure](#project-structure)
   1. [Complete programs](#complete-programs)
   2. [API](#api)
      1. [Annotations and errors](#annotations-and-errors)
8. [Building and running](#building-and-running)
   1. [Program execution with Maven](#program-execution-with-maven)
   2. [Using Snow in your own projects](#using-snow-in-your-own-projects)
9. [The linter](#the-linter)
   1. [Doing your own linting](#doing-your-own-linting)
      1. [Linting by traversing the tree](#linting-by-traversing-the-tree)
10. [The coverage checker](#the-coverage-checker)
11. [Future plans](#future-plans)
    1. [Possible future plans](#possible-future-plans)
12. [References](#references)
13. [An ending thought](#an-ending-thought)
14. [License](#license)

## Features

This project has the following features:

1. Full support for all drafts since Draft-06.
2. Full "format" validation support, with a few platform-specific exceptions.
3. Full annotation and error support.
   1. There is enough information to provide full output support. The calling
      app has to sort through and format what it wants, however.
4. Written for correctness. This aims to be a reference implementation.
5. Can initialize with known URIs. These can be any of:
   1. Parsed JSON objects.
   2. URLs. The URLs can be anything, including filesystem locations, web
      resources, and other objects. "URL" here means that the system knows how
      to retrieve something vs. "URI", which is just an ID.
6. Options for controlling "format" validation, annotation collection, error
   collection, and default and non-default specification choice.
7. There's rudimentary infinite loop detection, but only if error or annotation
   collection is enabled. It works by detecting that a previous keyword has been
   applied to the same instance location.
8. Specification detection heuristics for when there's no $schema declared.
9. Content validation support for the "base64" encoding and "application/json"
   media type.

### Additional features

These additional features exist:

1. A rudimentary linter that catches simple but common errors.
2. A coverage tool.

## Quick start

There are more details below, but here are four commands that will get you
started right away:

1. Run the validator on an instance against a schema:
   ```bash
   mvn compile exec:java@main -Dexec.args="schema.json instance.json"
   ```
   The two files in this example are named `schema.json` for the schema and
   `instance.json` for the instance. The example assumes the files are in the
   current working directory.
2. Clone and then run the test suite:
   ```bash
   mvn compile exec:java@test -Dexec.args="/suites/json-schema-test-suite"
   ```
   This assumes that the test suite is in `/suites/json-schema-test-suite`.
   Yours may be in a different location. The test suite can be cloned from
   [JSON Schema Test Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite).
3. Run the linter on a schema:
   ```bash
   mvn compile exec:java@linter -Dexec.args="schema.json"
   ```
   The schema file in this example is named `schema.json`. The example assumes
   the file is in the current working directory.
4. Run the schema coverage checker after a validation:
   ```bash
   mvn compile exec:java@coverage -Dexec.args="schema.json instance.json"
   ```
   The two files in this example are named `schema.json` for the schema and
   `instance.json` for the instance. The example assumes the files are in the
   current working directory.

## Under the covers

This project uses Google's [Gson](https://github.com/google/gson) and
[Guava](https://github.com/google/guava) libraries under the hood. Gson is used
for the JSON parsing, and Guava is used to support class finding.

This means these things:
1. The external API for this project uses Gson's JSON object model.

## Limitations

This project follows just about everything it can from the latest JSON Schema
specification draft. There are a few things it does slightly differently due to
some implementation details.

1. Regular expressions allow or disallow some things that
   [ECMA 262](https://www.ecma-international.org/publications/standards/Ecma-262.htm)
   regular expressions do not. For example, Java allows the `\Z` boundary
   matcher but ECMA 262 does not.

## Which specification is used for processing?

There are a few ways the validator determines which specification to use when
processing and validating a schema. The steps are as follows:

1. $schema value. If the schema explicitly specifies this value, and if it is
   known by the validator, then this is the specification that the validator
   will use.
2. The `SPECIFICATION` option or any default.
3. Guessed by heuristics.
4. The `DEFAULT_SPECIFICATION` option or any default.
5. Not known.

## Options for controlling behaviour

This section describes options that control the validator behaviour.

All options are defined in the `com.qindesign.json.schema.Option` class, and
their use is in `com.qindesign.json.schema.Options`.

Some options are specification-specific, meaning they have different defaults
depending on which specification is applied. Everything else works as expected:
users set or remove options. It is only the internal defaults that have any
specification-specific meanings.

There are two ways to retrieve an option. Both are similar, except one of the
ways checks the specification-specific defaults before the
non-specification-specific defaults. The steps are as follows, where subsequent
steps are followed only if the current step is not successful.

Specification-specific consultation steps, using a specific specification:

1. Options set by the user.
2. Specification-specific defaults.
3. Non-specification-specific defaults.
4. Not found.

Non-specification-specific consultation steps:

1. Options set by the user.
2. Non-specification-specific defaults.
3. Not found.

### Option: AUTO_RESOLVE

Type: `java.lang.Boolean`

This controls whether the validator should attempt auto-resolution when
searching for schemas or when otherwise resolving IDs. This entails adding the
original base URI and any root $id as known URLs during validation.

### Option: COLLECT_ANNOTATIONS_FOR_FAILED

Type: `java.lang.Boolean`

This controls, if annotations are collected, whether they should also be
retained for failed schemas. This option only has an effect when annotations are
being collected.

### Option: CONTENT

Type: `java.lang.Boolean`

This controls whether to treat the "content" values as assertions. Currently,
this includes "contentEncoding" and "contentMediaType".

### Option: DEFAULT_SPECIFICATION

Type: `com.qindesign.json.schema.Specification`

This option specifies the default specification to follow if one cannot be
determined from a schema, either by an explicit indication, or by heuristics.
This is the final fallback specification.

### Option: FORMAT

Type: `java.lang.Boolean`

This is a specification-specific option meaning its default is different
depending on which specification is being used. It controls whether to treat
"format" values as assertions.

### Option: SPECIFICATION

Type: `com.qindesign.json.schema.Specification`

This indicates which specification to use if one is not explicitly stated in
a schema.

## Project structure

This project is designed to provide APIs and tools for performing JSON Schema
validation. Its main purpose is to do most of the work, but have the user wire
in everything themselves. A few rudimentary and runnable test programs are
provided, however.

The main package is `com.qindesign.json.schema`.

### Complete programs

The first program is `Main`. This takes two arguments, a schema file and an
instance file, and then performs validation of the instance against the schema.

The second program is `Test`. This takes one argument, a directory containing
the JSON Schema test suite, and then runs all the tests in the suite. You can
obtain a copy of the test suite by cloning the
[test suite repository](https://github.com/json-schema-org/JSON-Schema-Test-Suite).

The third program is `Linter`, a rudimentary linter for JSON Schema files. It
takes one argument, the schema file to check.

The fouth program is `Coverage`, a simple coverage tool for JSON Schemas and
instances. It's similar to `Main`, but prints different output.

### API

The main entry point to the API is the `Validator` constructor and `validate`
method. In addition to the non-optional schema, instance, and base URI, you can
pass options, known IDs and URLs, and a place to put collected annotations and
errors.

In this version, the caller must organize the errors into the desired output
format. An example of how to convert them into the Basic output format is in
the `Main.basicOutput` method.

Providing tools to format the errors into more output formats may happen in
the future.

#### Annotations and errors

Annotations and errors are collected by optionally providing maps to
`Validator.validate`. They're maps from instance locations to an associated
`Annotation` object, with some intervening values.

* The annotations map follows this structure: instance location &rarr; name
  &rarr; schema location &rarr; `Annotation`. The `Annotation` value is
  dependent on the source of the annotation.
* The errors map has this structure: instance location &rarr; schema location
  &rarr; `Annotation`. The `Annotation` value is a `ValidationResult` object,
  and its name will be "error" when the result is `false` and "annotation" when
  the result is `true`.

In all cases, `Annotation.isValid()` indicates whether the annotation is
considered valid or auxiliary. When
[failed annotations](#option-collect_annotations_for_failed) are collected,
invalid annotations indicate an annotation that would otherwise exist if the
associated schema had not failed.

For errors, an invalid annotation means that the instance location is valid, but
the associated schema application failed. For example, "oneOf" will pass
validation if one subschema passes and all the other subschemas fail. All
failing subschemas will indicate an error, but the annotation will be marked as
not valid.

This is useful to track coverage vs. a minimal set of useful errors.

The locations are given as
[JSON Pointers](https://www.rfc-editor.org/rfc/rfc6901.html).

The annotation types for specific keywords are as follows:
* "additionalItems": `java.lang.Boolean`, always `true` if present, indicating
  that the subschema was applied to all remaining items in the instance array.
* "additionalProperties": `java.util.Set<String>`, the set of property names
  whose contents were validated by this subschema.
* "contentEncoding": `java.lang.String`
* "contentMediaType": `java.lang.String`
* "contentSchema": `com.google.gson.JsonElement`
* "default": `com.google.gson.JsonElement`
* "deprecated": `java.lang.Boolean`
* "description": `java.lang.String`
* "examples": `com.google.gson.JsonArray`
* "format": `java.lang.String`
* "items": `java.lang.Integer`, the largest index in the instance to which a
  subschema was applied, or `java.lang.Boolean` (always `true`) if a subschema
  was applied to every index.
* "patternProperties": `java.util.Set<String>`, the set of property names
  matched by this keyword.
* "properties": `java.util.Set<String>`, the set of property names matched by
  this keyword.
* "readOnly": `java.lang.Boolean`
* "title": `java.lang.String`
* "unevaluatedItems": `java.lang.Boolean`, always `true` if present, indicating
  that the subschema was applied to all remaining items in the instance array.
* "unevaluatedProperties": `java.util.Set<String>`, the set of property names
  whose contents were validated by this subschema.
* "writeOnly": `java.lang.Boolean`

## Building and running

This project uses Maven as its build tool because it makes managing the
dependencies easy. It uses standard Maven commands and phases. For example, to
compile the project, use:

```bash
mvn compile
```

To clean and then re-compile:

```bash
mvn clean compile
```

Maven makes it easy to build, execute, and package everything with the right
dependencies, however it's also possible to use your IDE or different tools
to manage the project.  This section only discusses Maven usage.

### Program execution with Maven

Maven takes care of project dependencies for you so you don't have to manage the
classpath or downloads.

Currently, there are four predefined execution targets:
1. `main`: Executes `Main`. Validates an instance against a schema.
2. `test`: Executes `Test`. Runs the test suite.
3. `linter`: Executes `Linter`. Checks a schema.
4. `coverage`: Executed `Coverage`. Does a schema coverage check
   after validation.

This section shows some simple execution examples. There's more information
about the included programs below.

Note that Maven doesn't automatically build the project when running an
execution target. It either has to be pre-built using `compile` or added to the
command line. For example, to compile and then run the linter:

```bash
mvn compile exec:java@linter -Dexec.args="schema.json"
```

To run the main validator without attempting a compile first, say because it's
already built:

```bash
mvn exec:java@main -Dexec.args="schema.json instance.json"
```

To compile and run the test suite and tell the test runner that the suite is
in `/suites/json-schema-test-suite`:

```bash
mvn compile exec:java@test -Dexec.args="/suites/json-schema-test-suite"
```

To execute a specific main class, say one that isn't defined as a specific
execution, add an `exec.mainClass` property. For example, if the fully-qualified
main class is `my.Main` and it takes some "program arguments":

```bash
mvn exec:java -Dexec.mainClass="my.Main" -Dexec.args="program arguments"
```

### Using Snow in your own projects

Snow is available from the Maven Central Repository. To include it in your own
programs, add the following dependency:

```xml
<dependency>
  <groupId>com.qindesign</groupId>
  <artifactId>snowy-json</artifactId>
  <version>0.14.0</version>
</dependency>
```

## The linter

The linter's job is to provide suggestions about potential errors in a schema.
It shows only potential problems whose presence does not necessarily mean the
schema won't work.

The linter is rudimentary and does not check or validate everything about the
schema. It does currently check for the following things:

1. Unknown `format` values.
2. Empty `items` arrays.
3. `additionalItems` without a sibling array-form `items`.
4. `$schema` elements inside a subschema that do not have a sibling `$id`.
5. Unknown keywords.
6. Property names that start with "$".
7. Unnormalized `$id` values.
8. Locally-pointing `$ref` values that don't exist.
9. Any "minimum" keyword that is greater than its corresponding "maximum"
   keyword. For example, `minLength` and `maxLength`.
10. `exclusiveMinimum` is not strictly less than `exclusiveMaximum`.
11. Expected type checking for appropriate keywords. For example, `minimum`
    expects that the type is "number" or "integer" and `format` expects a
    "string" type.
12. Draft 2019-09 or later schemas having keywords that were removed in
    Draft 2019-09.
13. Pre-Draft 2019-09 schemas having keywords that were added in Draft 2019-09.
14. Pre-Draft-07 schemas having keywords that were added in Draft-07.
15. Draft 2019-09 or later, or unspecified, schemas:
    1. `minContains` without a sibling `contains`.
    2. `maxContains` without a sibling `contains`.
    3. `unevaluatedItems` without a sibling array-form `items`.
    4. `$id` values that have an empty fragment.
16. Draft-07 or later, or unspecified, schemas:
    1. `then` without `if`.
    2. `else` without `if`.
17. Draft-07 or earlier schemas:
    1. `$ref` members with siblings.

### Doing your own linting

It's possible to add your own rules to the linter. There are four important
concepts to know about when adding rules:
1. A rule may optionally be assigned to execute for a specific element type. For
   example, a rule added via `Linter.addStringRule` will execute if the current
   element is a primitive string.
2. A rule learns about the current state of the JSON tree from a context object
   parameter, an instance of `Linter.Context`.
3. Any detected issues are sent to the context.
4. The rules operate in addition to the existing linter rules.

The following example snippet tests for the existence of any "anyOf"
schema keywords:

```java
JsonElement schema;
// ...load the schema...
Linter linter = new Linter();
linter.addRule(context -> {
  if (context.isKeyword() && context.is("anyOf")) {
    context.addIssue("anyOf detected");
  }
});
Map<JSONPath, List<String>> issues = linter.check();
// ...print the issues...
```

#### Linting by traversing the tree

The `JSON` class has a `traverseSchema` method that does a preorder tree
traversal for JSON schemas. It's what the linter uses internally. It's also
possible to use this to write your own linting rules.

The following example snippet also tests for the existence of any "anyOf"
schema keywords:

```java
JsonElement schema;
// ...load the schema...
JSON.traverseSchema(schema, (e, parent, path, state) -> {
  if (!state.isNotKeyword() && path.endsWith("anyOf")) {
    System.out.println(path + ": anyOf keyword present");
  }
});
```

## The coverage checker

The coverage checker works similarly to the main validator, except that after
validation, it prints out some coverage results.

It outputs two JSON objects:
1. Seen and unseen schema locations organized by instance location.
2. Seen schema locations only.

## Future plans

There are plans to explore supporting more features, including:

1. Custom vocabulary support.
2. More output formatting. All the information is currently there, but the
   caller must process and organize it.
3. Better caching. The current implementation doesn't cache things such as URLs
   and regex Patterns across different instances of `ValidatorContext`, i.e.
   across calls to `Validator.validate`.
4. Compilation into an internal representation that provides both speed and
   optimizations for non-dynamic validation paths.
5. A better representation than maps for annotations and errors.

### Possible future plans

These are plans that may or may not be explored:

1. Linter rule IDs for selective linting.

## References

1. [JSON Schema Specification](https://json-schema.org/specification.html)
2. [Gson](https://github.com/google/gson)
3. [Guava](https://github.com/google/guava)
4. [ECMA 262](https://www.ecma-international.org/publications/standards/Ecma-262.htm)
5. [JSON Schema Test Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
6. [JSON Schema Draft Sources](https://github.com/json-schema-org/json-schema-spec)
7. [JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901.html)
8. [URI Syntax](https://www.rfc-editor.org/rfc/rfc3986.html)
9. [IDN Hostnames](https://www.rfc-editor.org/rfc/rfc5890.html)

## An ending thought

I'd love to say this: "The validator isn't wrong, the spec is ambiguous."™ \
Realistic? No, but fun to say anyway.

## License

```
Snow, a JSON Schema validator
Copyright (c) 2020  Shawn Silverman

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

---
Copyright (c) 2020 Shawn Silverman
