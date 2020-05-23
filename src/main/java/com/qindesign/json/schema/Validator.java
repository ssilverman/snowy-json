/*
 * Created by shawn on 4/22/20 10:38 AM.
 */
package com.qindesign.json.schema;

import com.google.common.graph.Traverser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.CoreRef;
import com.qindesign.json.schema.keywords.CoreSchema;
import com.qindesign.json.schema.keywords.Format;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Validator tools.
 */
public final class Validator {
  private static final Class<?> CLASS = Validator.class;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  // https://www.baeldung.com/java-initialize-hashmap
  private static final Map<URI, URL> KNOWN_RESOURCES = Stream.of(new Object[][] {
      { "https://json-schema.org/draft/2019-09/schema", "/draft-2019-09/schema.json" },
      { "https://json-schema.org/draft/2019-09/meta/core", "/draft-2019-09/core.json" },
      { "https://json-schema.org/draft/2019-09/meta/applicator", "/draft-2019-09/applicator.json" },
      { "https://json-schema.org/draft/2019-09/meta/validation", "/draft-2019-09/validation.json" },
      { "https://json-schema.org/draft/2019-09/meta/meta-data", "/draft-2019-09/meta-data.json" },
      { "https://json-schema.org/draft/2019-09/meta/format", "/draft-2019-09/format.json" },
      { "https://json-schema.org/draft/2019-09/meta/content", "/draft-2019-09/content.json" },
      { "http://json-schema.org/draft-07/schema", "/draft-07/schema.json" },
      { "http://json-schema.org/draft-06/schema", "/draft-06/schema.json" },
      }).collect(Collectors.toUnmodifiableMap(
          data -> URI.create((String) data[0]),
          data -> {
            URL url = CLASS.getResource((String) data[1]);
            if (url == null) {
              throw new MissingResourceException(
                  "Can't find resource \"" + data[1] + "\" in " + CLASS.getName(),
                  CLASS.getName(), (String) data[1]);
            }
            return url;
          }));

  private static final Set<URI> KNOWN_SCHEMAS = Arrays.stream(Specification.values())
      .map(Specification::id)
      .collect(Collectors.toUnmodifiableSet());

  private static final Set<String> NEW_KEYWORDS_DRAFT_2019_09 = Set.of(
      "$anchor",
      "$defs",
      "$recursiveAnchor",
      "$recursiveRef",
      "$vocabulary",
      "dependentSchemas",
      "unevaluatedItems",
      "unevaluatedProperties",
      "dependentRequired",
      "maxContains",
      "minContains",
      "contentSchema",
      "deprecated");
  private static final Set<String> OLD_KEYWORDS_DRAFT_2019_09 = Set.of(
      "definitions",
      "dependencies");
  private static final Set<String> NEW_FORMATS_DRAFT_2019_09 = Set.of(
      "duration",
      "uuid");
  private static final Set<String> NEW_KEYWORDS_DRAFT_07 = Set.of(
      "$comment",
      "if",
      "then",
      "else",
      "readOnly",
      "writeOnly",
      "contentMediaType",
      "contentEncoding");
  private static final Set<String> NEW_FORMATS_DRAFT_07 = Set.of(
      "iri",
      "iri-reference",
      "idn-email",
      "idn-hostname",
      "relative-json-pointer",
      "regex",
      "date",
      "time");

  /**
   * @see <a href="https://www.w3.org/TR/2006/REC-xml-names11-20060816/#NT-NCName">Namespaces in XML 1.1 (Second Edition): NCName</a>
   */
  public static final java.util.regex.Pattern ANCHOR_PATTERN =
      java.util.regex.Pattern.compile("[A-Z_a-z][-A-Z_a-z.0-9]*");

  private Validator() {
  }

  /**
   * Convenience method that checks if a JSON element is a Boolean.
   *
   * @param e the element to test
   * @return whether the element is a Boolean.
   */
  public static boolean isBoolean(JsonElement e) {
    return e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
  }

  /**
   * Convenience method that checks if a JSON element is potential schema. A
   * schema is either an object or Boolean. Note that this does not do a deep
   * check if the element is an object.
   *
   * @param e the element to test
   * @return whether the element is a Boolean.
   */
  public static boolean isSchema(JsonElement e) {
    return e.isJsonObject() || isBoolean(e);
  }

  /**
   * Convenience method that checks if a JSON element is a number.
   *
   * @param e the element to test
   * @return whether the element is a number.
   */
  public static boolean isNumber(JsonElement e) {
    return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
  }

  /**
   * Convenience method that checks if a JSON element is a string.
   *
   * @param e the element to test
   * @return whether the element is a string.
   */
  public static boolean isString(JsonElement e) {
    return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
  }

  /**
   * Validates an instance against a schema. Known JSON contents and resources
   * can be added, however the IDs in the schema will override those resources
   * if there are duplicates.
   * <p>
   * When searching for resources, {@code knownIDs} is searched first and
   * {@code knownURLs} is searched second.
   * <p>
   * The default specification is given, but if one can be determined from the
   * schema then that is used instead.
   *
   * @param schema the schema
   * @param instance the instance
   * @param baseURI the schema's base URI
   * @param knownIDs any known JSON contents, searched first
   * @param knownURLs any known resources, searched second
   * @param opts any options
   * @return the validation result.
   * @throws MalformedSchemaException if the schema is somehow malformed.
   */
  public static boolean validate(JsonElement schema, JsonElement instance,
                                 URI baseURI,
                                 Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                                 Options opts)
      throws MalformedSchemaException
  {
    if (opts == null) {
      opts = new Options();
    }

    // First, determine the schema specification
    Specification spec = specificationFromSchema(schema);

    // If there's no explicit specification, try to guess it and then fall back
    // on the default specification
    boolean isDefaultSpec = (spec == null);
    if (isDefaultSpec) {
      spec = guessSpecification(schema);
      if (spec == null) {
        spec = (Specification) opts.get(Option.DEFAULT_SPECIFICATION);
      }
    }

    // Prepare the schema and instance
    // This is so we have a ValidatorContext we can use during schema validation
    var ids = scanIDs(baseURI, schema, spec);
    if (knownIDs != null) {
      knownIDs.forEach((uri, e) -> ids.putIfAbsent(new Id(uri), e));
    }
    if (knownURLs == null) {
      knownURLs = Collections.emptyMap();
    }

    // Assume all the known specs have been validated
    ValidatorContext context = new ValidatorContext(baseURI, ids, knownURLs, KNOWN_SCHEMAS, opts);

    // If the spec is known, the $schema keyword will process it
    // Next, validate the schema if it's unknown
    if (isDefaultSpec && schema.isJsonObject()) {
      try {
        if (!new CoreSchema()
            .apply(new JsonPrimitive(spec.id().toString()), instance, schema.getAsJsonObject(),
                   context)) {
          throw new MalformedSchemaException("schema does not validate against " + spec.id(),
                                             baseURI);
        }
      } catch (MalformedSchemaException ex) {
        // Ignore a bad or unknown meta-schema
        // The whole point here, after all, is to get the vocabularies
      }
    }
    // TODO: I don't love the duplicate code in CoreSchema

    return context.apply(schema, "", instance, "");
  }

//  /**
//   * Validates a schema against its or a default meta-schema. If the meta-schema
//   * could not be found or is not valid then this will return {@code true}.
//   * <p>
//   * This uses the default specification if the schema does not proclaim one.
//   *
//   * @param schema the schema to validate
//   * @param defaultSpec the default specification
//   * @return whether the given schema validates.
//   */
//  public static boolean validateSchema(JsonElement schema, Specification defaultSpec)
//      throws MalformedSchemaException
//  {
//    // First, determine the schema specification
//    // Use all the things we know about $schema
//    Specification spec = null;
//    if (schema.isJsonObject()) {
//      JsonElement schemaVal = schema.getAsJsonObject().get(CoreSchema.NAME);
//      if (schemaVal != null && isString(schemaVal)) {
//        try {
//          URI uri = new URI(schemaVal.getAsString());
//          if (uri.isAbsolute() && uri.normalize().equals(uri)) {
//            spec = Specification.of(URIs.stripFragment(uri));
//          }
//        } catch (URISyntaxException ex) {
//          // Ignore
//        }
//      }
//    }
//
//    // If the spec is known, the $schema keyword will process it
//    if (spec != null) {
//      return true;
//    }
//
//    // Next, validate the schema if it's unknown
//    // Use the default specification
//    JsonElement metaSchema = loadResource(spec.id());
//    if (metaSchema == null) {
//      return true;
//    }
//
//    Map<Id, JsonElement> ids;
//    try {
//      ids = Validator.scanIDs(spec.id(), metaSchema, spec);
//    } catch (MalformedSchemaException ex) {
//      // Assume a bad known schema validates
//      return true;
//    }
//  }

  /**
   * Loads a resource as JSON. This returns {@code null} if the resource could
   * not be found.
   *
   * @param uri the resource ID
   * @return the resource, or {@code null} if the resource could not be found.
   */
  public static JsonElement loadResource(URI uri) {
    URL url = KNOWN_RESOURCES.get(uri);
    if (url == null) {
      return null;
    }
    try (InputStream in = url.openStream()) {
      if (in == null) {
        return null;
      }
      try {
        return JSON.parse(in);
      } catch (JsonParseException ex) {
        logger.log(Level.SEVERE, "Error parsing resource: " + uri, ex);
      }
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error loading resource: " + uri, ex);
    }
    return null;
  }

  /**
   * Examines the schema for any $schema keyword to see if the value is valid
   * and known. This will return that value, or {@code null} if the valus is
   * invalid or unknown. This will return {@code null} if the schema is not
   * an object, i.e. for Boolean and non-object values.
   *
   * @param schema the schema
   * @return the valid and known $schema value, or {@code null} otherwise.
   */
  public static Specification specificationFromSchema(JsonElement schema) {
    if (!schema.isJsonObject()) {
      return null;
    }

    // Use all the things we know about $schema
    JsonElement schemaVal = schema.getAsJsonObject().get(CoreSchema.NAME);
    if (schemaVal != null && isString(schemaVal)) {
      try {
        URI uri = new URI(schemaVal.getAsString());
        // Don't check if it's normalized because it may become a valid spec
        if (uri.isAbsolute()) {
          return Specification.of(URIs.stripFragment(uri.normalize()));
        }
      } catch (URISyntaxException ex) {
        // Ignore and continue
      }
    }

    return null;
  }

  /**
   * Guesses the specification of a schema. This is for use after determining
   * that a $schema value is neither available nor known; any existing $schema
   * in the object is not used.
   * <p>
   * The heuristics are:
   * <ul>
   * <li>
   *   Examine any $ref to see if the value is valid and known as a schema
   * </li>
   * <li>Keywords present in only one of the specs</li>
   * <li>Formats present in only one of the specs</li>
   * </ul>
   * <p>
   * This returns {@code null} if a specification could not be identified or if
   * none of the specifications are likely to be valid.
   *
   * @param schema the schema object
   * @return the specification, or {@code null} if one could not be determined.
   * @see #specificationFromSchema(JsonElement)
   */
  @SuppressWarnings("UnstableApiUsage")
  public static Specification guessSpecification(JsonElement schema) {
    if (!schema.isJsonObject()) {
      return null;
    }

    // TODO: Even more heuristics

    // See if there's a $ref to a schema
    JsonElement refVal = schema.getAsJsonObject().get(CoreRef.NAME);
    if (refVal != null && isString(refVal)) {
      try {
        URI uri = new URI(refVal.getAsString());
        // Don't check if it's normalized because it may become a valid spec
        if (uri.isAbsolute()) {
          return Specification.of(URIs.stripFragment(uri.normalize()));
        }
      } catch (URISyntaxException ex) {
        // Ignore and continue
      }
    }

    // Heuristics

    // New keywords in Draft 2019-09:
    // * Core
    //   * $anchor
    //   * $defs
    //   * $recursiveAnchor
    //   * $recursiveRef
    //   * $vocabulary
    // * Applicator
    //   * dependentSchemas
    //   * unevaluatedItems
    //   * unevaluatedProperties
    // * Validation
    //   * dependentRequired
    //   * maxContains
    //   * minContains
    // * Content
    //   * contentSchema
    // * Meta-data
    //   * deprecated
    //
    // Formats; may have been used flexibly
    // * "duration"
    // * "uuid"
    //
    // Semantic changes:
    // * No plain name fragments in $id
    //   * May be erroneously present
    // * $ref allows siblings
    //   * May be erroneously in earlier drafts

    // Keywords removed from Draft-07
    // * definitions
    //   * May erroneously be in later drafts
    // * dependencies

    // New keywords in Draft-07:
    // * Core
    //   * $comment
    // * Validation
    //   * if, then, else
    //   * readOnly
    //   * writeOnly
    //   * contentMediaType
    //   * contentEncoding
    //
    // Formats; may have been used flexibly
    // * "iri"
    // * "iri-reference"
    // * "idn-email"
    // * "idn-hostname"
    // * "relative-json-pointer"
    // * "regex"
    // * "date"
    // * "time"

    Set<Specification> couldBe = new HashSet<>();
    Set<Specification> cantBe = new HashSet<>();

    int specsSize = Specification.values().length;

    // Collect everything into "could be" and "can't be" sets
    Traverser<JsonObject> traverser = Traverser.forTree(
        node ->
            node.entrySet().stream()
                .filter(e -> e.getValue().isJsonObject())
                .map(e -> e.getValue().getAsJsonObject())
                .collect(Collectors.toUnmodifiableList()));
    StreamSupport
        .stream(traverser.depthFirstPreOrder(schema.getAsJsonObject()).spliterator(), false)
        .flatMap(o -> o.entrySet().stream())
        .allMatch(e -> {
          // Look into the keyword
          switch (e.getKey()) {
            case CoreId.NAME:
              if (isString(e.getValue())) {
                try {
                  if (URIs.hasNonEmptyFragment(new URI(e.getValue().getAsString()))) {
                    cantBe.add(Specification.DRAFT_2019_09);
                    couldBe.add(Specification.DRAFT_07);
                    couldBe.add(Specification.DRAFT_06);
                  } else {
                    couldBe.add(Specification.DRAFT_2019_09);
                    couldBe.add(Specification.DRAFT_07);
                    couldBe.add(Specification.DRAFT_06);
                  }
                } catch (URISyntaxException ex) {
                  // Ignore and continue
                }
              }
              break;

            case Format.NAME:
              if (isString(e.getValue())) {
                String format = e.getValue().getAsString();
                if (NEW_FORMATS_DRAFT_2019_09.contains(format)) {
                  couldBe.add(Specification.DRAFT_2019_09);
                  cantBe.add(Specification.DRAFT_07);
                  cantBe.add(Specification.DRAFT_06);
                } else if (NEW_FORMATS_DRAFT_07.contains(format)) {
                  couldBe.add(Specification.DRAFT_2019_09);
                  couldBe.add(Specification.DRAFT_07);
                  cantBe.add(Specification.DRAFT_06);
                } else {
                  couldBe.add(Specification.DRAFT_2019_09);
                  couldBe.add(Specification.DRAFT_07);
                  couldBe.add(Specification.DRAFT_06);
                }
              }
              break;

            default:
              if (NEW_KEYWORDS_DRAFT_2019_09.contains(e.getKey())) {
                // Ignore if there are old keywords because they'll get ignored
                // during processing
                couldBe.add(Specification.DRAFT_2019_09);
                cantBe.add(Specification.DRAFT_07);
                cantBe.add(Specification.DRAFT_06);
              } else if (NEW_KEYWORDS_DRAFT_07.contains(e.getKey())) {
                couldBe.add(Specification.DRAFT_2019_09);
                couldBe.add(Specification.DRAFT_07);
                cantBe.add(Specification.DRAFT_06);
              } else if (OLD_KEYWORDS_DRAFT_2019_09.contains(e.getKey())) {
                cantBe.add(Specification.DRAFT_2019_09);
                couldBe.add(Specification.DRAFT_07);
                couldBe.add(Specification.DRAFT_06);
              } else {
                couldBe.add(Specification.DRAFT_2019_09);
                couldBe.add(Specification.DRAFT_07);
                couldBe.add(Specification.DRAFT_06);
              }
          }

          // Don't continue if it can't be any of the specs
          return (cantBe.size() < specsSize);
        });

    // Remove all the "can't be" values from the "could be" set
    return couldBe.stream()
        .filter(spec -> !cantBe.contains(spec))
        .max(Comparator.naturalOrder()).orElse(null);
  }

  /**
   * Scans all the IDs in a JSON document starting from a given base URI. The
   * base URI is the initial known document resource ID. It will be normalized
   * and have any fragment, even an empty one, removed.
   * <p>
   * This will return at least one ID mapping to the base element, which may
   * be redefined by an ID element.
   * <p>
   * The given specification is the one used for processing. It can be
   * identified by the caller via a call to
   * {@link #specificationFromSchema(JsonElement)} and
   * {@link #guessSpecification(JsonElement)}
   *
   * @param baseURI the base URI
   * @param e the JSON document
   * @param spec the specification to use for processing
   * @return a map of IDs to JSON elements.
   * @throws IllegalArgumentException if the base URI has a non-empty fragment.
   * @throws MalformedSchemaException if the schema is considered malformed.
   * @see #specificationFromSchema(JsonElement)
   * @see #guessSpecification(JsonElement)
   */
  public static Map<Id, JsonElement> scanIDs(URI baseURI, JsonElement e, Specification spec)
      throws MalformedSchemaException {
    if (URIs.hasNonEmptyFragment(baseURI)) {
      throw new IllegalArgumentException("Base UI has a non-empty fragment");
    }
    baseURI = URIs.stripFragment(baseURI).normalize();

    Map<Id, JsonElement> ids = new HashMap<>();
    URI newBase = scanIDs(baseURI, baseURI, baseURI, null, "", null, e, ids, spec);

    // Ensure we have at least the base URI, if it's not already there
    Id id = new Id(baseURI);
    id.value = null;
    id.base = null;
    id.path = "";
    id.root = newBase;
    id.rootURI = baseURI;
    ids.putIfAbsent(id, e);

    return ids;
  }

  /**
   * Scans a JSON element for IDs. The element is described by the
   * {@code parentID} combined with the {@code name}.
   *
   * @param rootURI the URI of the document
   * @param rootID the defined root ID, may be the same as {@code rootID}
   * @param baseURI the current base ID
   * @param parentID the parent ID
   * @param name the name of the current element, a number for an array element
   * @param parent the parent of the given element
   * @param e the element to scan
   * @param ids the ID map
   * @param spec the current specification
   * @return the root ID, may change from the original.
   * @throws MalformedSchemaException if the schema is considered malformed
   *         vis-à-vis IDs.
   */
  private static URI scanIDs(URI rootURI, URI rootID, URI baseURI,
                             String parentID, String name,
                             JsonElement parent, JsonElement e,
                             Map<Id, JsonElement> ids,
                             Specification spec)
      throws MalformedSchemaException {
    if (e.isJsonPrimitive() || e.isJsonNull()) {
      return rootID;
    }

    // Create the parent ID of the processed sub-elements
    String newParentID;
    if (parent == null) {
      newParentID = "";
    } else {
      name = name.replace("~", "~0");
      name = name.replace("/", "~1");
      newParentID = parentID + "/" + name;
    }

    if (e.isJsonArray()) {
      int index = 0;
      for (var elem : e.getAsJsonArray()) {
        scanIDs(rootURI, rootID, baseURI, newParentID, Integer.toString(index++), e, elem, ids,
                spec);
      }
      return rootID;
    }

    // Don't look at the $id or $anchor values inside properties
    boolean inProperties = name.equals("properties");

    // Process any "$id"
    JsonElement value;

    if (!inProperties) {
      value = e.getAsJsonObject().get("$id");
      if (value != null) {
        String path = newParentID + "/$id";

        if (!isString(value)) {
          throw new MalformedSchemaException("not a string", Strings.jsonPointerToURI(path));
        }

        URI uri;
        try {
          uri = URI.create(value.getAsString()).normalize();
        } catch (IllegalArgumentException ex) {
          throw new MalformedSchemaException("not a valid URI-reference",
                                             Strings.jsonPointerToURI(path));
        }

        if (URIs.hasNonEmptyFragment(uri)) {
          if (spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
            throw new MalformedSchemaException("has a non-empty fragment",
                                               Strings.jsonPointerToURI(path));
          }

          if (!ANCHOR_PATTERN.matcher(uri.getRawFragment()).matches()) {
            throw new MalformedSchemaException("invalid plain name",
                                               Strings.jsonPointerToURI(path));
          }

          Id id = new Id(baseURI.resolve(uri));
          id.value = value.getAsString();
          id.base = baseURI;
          id.path = newParentID;
          id.root = rootID;
          id.rootURI = rootURI;
          if (ids.put(id, e) != null) {
            throw new MalformedSchemaException(
                "anchor not unique: name=" + id.value +
                " base=" + id.base + " rootID=" + id.root + " rootURI=" + id.rootURI,
                Strings.jsonPointerToURI(newParentID));
          }
        } else {
          uri = URIs.stripFragment(uri);

          Id id = new Id(baseURI.resolve(uri));
          id.value = value.getAsString();
          id.base = baseURI;
          id.path = newParentID;
          id.root = rootID;
          id.rootURI = rootURI;
          if (ids.put(id, e) != null) {
            throw new MalformedSchemaException("ID not unique",
                                               Strings.jsonPointerToURI(newParentID));
          }

          baseURI = id.id;
          if (parent == null) {
            rootID = id.id;
          }
        }
      }

      // Process any "$anchor"
      if (spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        value = e.getAsJsonObject().get("$anchor");
        if (value != null) {
          String path = newParentID + "/$anchor";

          if (!isString(value)) {
            throw new MalformedSchemaException("not a string",
                                               Strings.jsonPointerToURI(path));
          }
          if (!ANCHOR_PATTERN.matcher(value.getAsString()).matches()) {
            throw new MalformedSchemaException("invalid plain name",
                                               Strings.jsonPointerToURI(path));
          }

          Id id = new Id(baseURI.resolve("#" + value.getAsString()));
          id.value = value.getAsString();
          id.base = baseURI;
          id.path = newParentID;
          id.root = rootID;
          id.rootURI = rootURI;
          if (ids.put(id, e) != null) {
            throw new MalformedSchemaException(
                "anchor not unique: name=" + id.value +
                " base=" + id.base + " rootID=" + id.root + " rootURI=" + id.rootURI,
                Strings.jsonPointerToURI(newParentID));
          }
        }
      }
    }  // !inProperties

    // Process everything else
    // Only process $id and $anchor if not inside properties
    for (var entry : e.getAsJsonObject().entrySet()) {
      if (!inProperties) {
        if (entry.getKey().equals("$id") || entry.getKey().equals("$anchor")) {
          continue;
        }
      }
      scanIDs(rootURI, rootID, baseURI, newParentID, entry.getKey(), e, entry.getValue(), ids,
              spec);
    }

    return rootID;
  }
}
