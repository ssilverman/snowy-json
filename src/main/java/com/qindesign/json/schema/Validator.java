/*
 * Snow, a JSON Schema validator
 * Copyright (c) 2020  Shawn Silverman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Created by shawn on 4/22/20 10:38 AM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.qindesign.json.schema.keywords.ContentEncoding;
import com.qindesign.json.schema.keywords.ContentMediaType;
import com.qindesign.json.schema.keywords.ContentSchema;
import com.qindesign.json.schema.keywords.CoreAnchor;
import com.qindesign.json.schema.keywords.CoreComment;
import com.qindesign.json.schema.keywords.CoreDefs;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.CoreRecursiveAnchor;
import com.qindesign.json.schema.keywords.CoreRecursiveRef;
import com.qindesign.json.schema.keywords.CoreRef;
import com.qindesign.json.schema.keywords.CoreSchema;
import com.qindesign.json.schema.keywords.CoreVocabulary;
import com.qindesign.json.schema.keywords.Definitions;
import com.qindesign.json.schema.keywords.Dependencies;
import com.qindesign.json.schema.keywords.DependentRequired;
import com.qindesign.json.schema.keywords.DependentSchemas;
import com.qindesign.json.schema.keywords.Deprecated;
import com.qindesign.json.schema.keywords.Format;
import com.qindesign.json.schema.keywords.If;
import com.qindesign.json.schema.keywords.MaxContains;
import com.qindesign.json.schema.keywords.MinContains;
import com.qindesign.json.schema.keywords.Properties;
import com.qindesign.json.schema.keywords.ReadOnly;
import com.qindesign.json.schema.keywords.UnevaluatedItems;
import com.qindesign.json.schema.keywords.UnevaluatedProperties;
import com.qindesign.json.schema.keywords.WriteOnly;
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
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  public static final Set<String> NEW_KEYWORDS_DRAFT_2019_09 = Set.of(
      CoreAnchor.NAME,
      CoreDefs.NAME,
      CoreRecursiveAnchor.NAME,
      CoreRecursiveRef.NAME,
      CoreVocabulary.NAME,
      DependentSchemas.NAME,
      UnevaluatedItems.NAME,
      UnevaluatedProperties.NAME,
      DependentRequired.NAME,
      MaxContains.NAME,
      MinContains.NAME,
      ContentSchema.NAME,
      Deprecated.NAME);
  public static final Set<String> OLD_KEYWORDS_DRAFT_2019_09 = Set.of(
      Definitions.NAME,
      Dependencies.NAME);
  private static final Set<String> NEW_FORMATS_DRAFT_2019_09 = Set.of(
      "duration",
      "uuid");
  public static final Set<String> NEW_KEYWORDS_DRAFT_07 = Set.of(
      CoreComment.NAME,
      If.NAME,
      "then",
      "else",
      ReadOnly.NAME,
      WriteOnly.NAME,
      ContentMediaType.NAME,
      ContentEncoding.NAME);
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

  /**
   * Disallow instantiation.
   */
  private Validator() {
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
    return e.isJsonObject() || JSON.isBoolean(e);
  }

  /**
   * Validates an instance against a schema. Known JSON contents and resources
   * can be added, however the IDs in the schema will override those resources
   * if there are duplicates.
   * <p>
   * When searching for resources, {@code knownIDs} is searched first and
   * {@code knownURLs} is searched second.
   * <p>
   * The order for determining the specification to use when processing the
   * schema is as follows. Subsequent steps are only followed if a step fails to
   * find something.
   * <ol>
   * <li>$schema value</li>
   * <li>{@link Option#SPECIFICATION SPECIFICATION} option or any default</li>
   * <li>Guessed by heuristics</li>
   * <li>{@link Option#DEFAULT_SPECIFICATION DEFAULT_SPECIFICATION} option or
   *     any default</li>
   * </ol>
   * <p>
   * The annotations and errors are maps from instance locations to an
   * associated {@link Annotation}, with some intervening values. Locations are
   * given as <a href="https://tools.ietf.org/html/rfc6901">JSON Pointers</a>.
   * <ul>
   * <li>The annotations follow this structure: instance location &rarr; name
   *     &rarr; schema location &rarr; {@link Annotation}. The
   *     {@link Annotation} value is dependent on the source.</li>
   * <li>The errors have this structure: instance location &rarr; schema
   *     location &rarr; {@link Annotation}. The {@link Annotation} value is an
   *     instance of {@link ValidationResult}, and its name will be "error" when
   *     the result is {@code false} and "annotation" when the result is
   *     {@code true}.
   * </ul>
   *
   * @param schema the schema, must not be {@code null}
   * @param instance the instance, must not be {@code null}
   * @param baseURI the schema's base URI, must not be {@code null}
   * @param knownIDs any known JSON contents, searched first
   * @param knownURLs any known resources, searched second
   * @param options any options
   * @param annotations annotations get stored here, if not {@code null}
   * @param errors errors get stored here, if not {@code null}
   * @return the validation result.
   * @throws NullPointerException if {@code schema}, {@code instance}, or
   *         {@code baseURI} are {@code null}.
   * @throws MalformedSchemaException if the schema is somehow malformed.
   * @see <a href="https://tools.ietf.org/html/rfc6901>JSON Pointer</a>
   */
  public static boolean validate(JsonElement schema, JsonElement instance,
                                 URI baseURI,
                                 Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                                 Options options,
                                 Map<String, Map<String, Map<String, Annotation>>> annotations,
                                 Map<String, Map<String, Annotation>> errors)
      throws MalformedSchemaException
  {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(instance, "instance");
    Objects.requireNonNull(baseURI, "baseURI");

    if (options == null) {
      options = new Options();
    }

    // First, determine the schema specification
    Specification spec = specificationFromSchema(schema);

    // If there's no explicit specification, try to guess it and then fall back
    // on the default specification
    boolean isDefaultSpec = (spec == null);
    if (isDefaultSpec) {
      spec = (Specification) options.get(Option.SPECIFICATION);
      if (spec == null) {
        spec = guessSpecification(schema);
        if (spec == null) {
          spec = (Specification) options.get(Option.DEFAULT_SPECIFICATION);
        }
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

    // Annotations and errors collection
    if (annotations == null) {
      if (Boolean.TRUE.equals(options.get(Option.COLLECT_ANNOTATIONS))) {
        annotations = new HashMap<>();
      } else {
        annotations = Collections.emptyMap();
      }
    }
    if (errors == null) {
      if (Boolean.TRUE.equals(options.get(Option.COLLECT_ERRORS))) {
        errors = new HashMap<>();
      } else {
        errors = Collections.emptyMap();
      }
    }

    // Assume all the known specs have been validated
    ValidatorContext context =
        new ValidatorContext(baseURI, ids, knownURLs, KNOWN_SCHEMAS, options, annotations, errors);

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

    boolean retval = context.apply(schema, "", null, instance, "");
    if (retval) {
      context.addError(true, null);
    } else {
      context.addError(false, "schema didn't validate");
    }
    return retval;
  }

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
    if (schemaVal != null && JSON.isString(schemaVal)) {
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
  public static Specification guessSpecification(JsonElement schema) {
    if (!schema.isJsonObject()) {
      return null;
    }

    // TODO: Even more heuristics

    // See if there's a $ref to a schema
    JsonElement refVal = schema.getAsJsonObject().get(CoreRef.NAME);
    if (refVal != null && JSON.isString(refVal)) {
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

    // Collect everything into "could be" and "can't be" sets
    JSON.traverse(schema, (e, parent, path, state) -> {
      if (!e.isJsonObject()) {
        return;
      }

      e.getAsJsonObject().entrySet().forEach(entry -> {
        // Look into the keyword
        switch (entry.getKey()) {
          case CoreId.NAME:
            if (JSON.isString(entry.getValue())) {
              try {
                if (URIs.hasNonEmptyFragment(new URI(entry.getValue().getAsString()))) {
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
            if (JSON.isString(entry.getValue())) {
              String format = entry.getValue().getAsString();
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
            if (NEW_KEYWORDS_DRAFT_2019_09.contains(entry.getKey())) {
              // Ignore if there are old keywords because they'll get ignored
              // during processing
              couldBe.add(Specification.DRAFT_2019_09);
              cantBe.add(Specification.DRAFT_07);
              cantBe.add(Specification.DRAFT_06);
            } else if (NEW_KEYWORDS_DRAFT_07.contains(entry.getKey())) {
              couldBe.add(Specification.DRAFT_2019_09);
              couldBe.add(Specification.DRAFT_07);
              cantBe.add(Specification.DRAFT_06);
            } else if (OLD_KEYWORDS_DRAFT_2019_09.contains(entry.getKey())) {
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
//        return (cantBe.size() < specsSize);
        // TODO: Implement early breaking from the visiting
      });
    });

    if (cantBe.size() >= Specification.values().length) {
      return null;
    }
    // Remove all the "can't be" values from the "could be" set
    return couldBe.stream()
        .filter(spec -> !cantBe.contains(spec))
        .max(Comparator.naturalOrder()).orElse(null);
  }

  /**
   * Gets and processes the given ID element. This returns a URI suitable for
   * resolving against the current base URI. This will return {@code null} if
   * the ID does not represent a new base, for example if it's an anchor.
   *
   * @param idElem the ID element
   * @param loc the absolute path of the element
   * @return the processed ID, or {@code null} if it's not a new base.
   * @throws MalformedSchemaException if the ID is malformed.
   */
  public static URI getID(JsonElement idElem, Specification spec, URI loc)
      throws MalformedSchemaException {
    if (!JSON.isString(idElem)) {
      throw new MalformedSchemaException("not a string", loc);
    }

    URI id;
    try {
      id = new URI(idElem.getAsString()).normalize();
    } catch (URISyntaxException ex) {
      throw new MalformedSchemaException("not a valid URI-reference", loc);
    }

    if (URIs.hasNonEmptyFragment(id)) {
      if (spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        throw new MalformedSchemaException("has a non-empty fragment", loc);
      }

      if (!ANCHOR_PATTERN.matcher(id.getRawFragment()).matches()) {
        throw new MalformedSchemaException("invalid plain name", loc);
      }

      // If it's not just a fragment then it represents a new base URI
      if (id.getScheme() == null && id.getRawSchemeSpecificPart().isEmpty()) {
        id = null;
      }
    } else {
      id = URIs.stripFragment(id);
    }

    return id;
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
      newParentID = parentID + "/" + Strings.jsonPointerToken(name);
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
    boolean inProperties = name.equals(Properties.NAME);

    // Process any "$id"
    JsonElement value;

    if (!inProperties) {
      value = e.getAsJsonObject().get(CoreId.NAME);
      if (value != null) {
        String path = newParentID + "/" + CoreId.NAME;

        if (!JSON.isString(value)) {
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
        value = e.getAsJsonObject().get(CoreAnchor.NAME);
        if (value != null) {
          String path = newParentID + "/" + CoreAnchor.NAME;

          if (!JSON.isString(value)) {
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
        if (entry.getKey().equals(CoreId.NAME) || entry.getKey().equals(CoreAnchor.NAME)) {
          continue;
        }
      }
      scanIDs(rootURI, rootID, baseURI, newParentID, entry.getKey(), e, entry.getValue(), ids,
              spec);
    }

    return rootID;
  }
}
