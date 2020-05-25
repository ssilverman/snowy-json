# Snow, a JSON Schema Validator

Version: 0.6.0

The main goal of this project is to be a reference JSON Schema validator.

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

## Under the covers

This project uses Google's [Gson](https://github.com/google/gson) and
[Guava](https://github.com/google/guava) libraries under the hood. Gson is used
for the JSON parsing, and Guava is used to support things such as validation,
tree walking, and class finding.

This means several things:
1. The external API for this project uses Gson's JSON object model.
2. Some validation operations, such as for internationalized hostnames and IRIs,
   are processed according to earlier standards than what is specified in the
   JSON Schema specification. These will be updated when Guava is updated.

## Limitations

This project follows just about everything it can from the latest JSON Schema
specification draft. There are a few things it does slightly differently due to
some implementation details.

1. Internationalized hostnames and IRIs are not validated according to the
   latest required specifications. This is because the Guava library supports
   earlier specifications.
2. Regular expressions allow or disallow some things that
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

### Option: COLLECT_ANNOTATIONS

Type: `java.lang.Boolean`

This controls whether annotations are collected during validation. If neither
this nor `COLLECT_ERRORS` is set to `true` then no loop detection is available.

### Option: COLLECT_ERRORS

Type: `java.lang.Boolean`

This controls whether errors are collected during validation. An "error" is
effectively an annotation whose name is "error" for a false validation result,
and "annotation" for a validation result of true. If neither this nor
`COLLECT_ANNOTATIONS` is set to `true` then no loop detection is available.

This option may need to be set to `false` if a schema uses $recursiveRef.

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

## Future plans

There are plans to explore supporting more features, including:

1. Custom vocabulary support.
2. Output formatting. All the information is currently there, but 

## References

1. [JSON Schema Specification](https://json-schema.org/specification.html)
2. [Gson](https://github.com/google/gson)
3. [Guava](https://github.com/google/guava)
4. [ECMA 262](https://www.ecma-international.org/publications/standards/Ecma-262.htm)

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
(c) 2020 Shawn Silverman
