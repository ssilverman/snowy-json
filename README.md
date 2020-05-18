# Snow, a JSON Validator

Version: 0.4.0

The main goal of this project is to be a reference JSON validator.

## Features

This project has the following features:

1. Full support for all drafts since Draft-06.
2. Full “format” validation support, with a few platform-specific exceptions.
3. Full annotation and error support.
   1. There is enough information to provide full output support. The calling
      app has to sort through and format what it wants, however.
4. Written for correctness. This aims to be a reference implementation.
5. Can initialize with known URIs. These can be any of:
   1. Parsed JSON objects.
   2. URLs. The URLs can be anything, including filesystem locations, web
      resources, and other objects. "URL" here means that the system knows how
      to retrieve something vs. "URI", which is just an ID.
6. Options for controlling "format" validation, annotation collection, and
   error collection.
7. There's rudimentary infinite loop detection, but only if error or annotation
   collection is enabled. It works by detecting that a previous keyword has been
   applied to the same instance location.
8. Specification detection heuristics for when there's no $schema declared.
9. Content validation support for the "base64" encoding and "application/json"
   media type.

I'd love to say this: "The validator isn't wrong, the spec is ambiguous."™
Realistic? No, but fun to say anyway.

---
(c) 2020 Shawn Silverman
