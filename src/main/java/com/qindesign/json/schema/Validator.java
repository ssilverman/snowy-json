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
import com.qindesign.json.schema.keywords.ReadOnly;
import com.qindesign.json.schema.keywords.UnevaluatedItems;
import com.qindesign.json.schema.keywords.UnevaluatedProperties;
import com.qindesign.json.schema.keywords.WriteOnly;
import com.qindesign.json.schema.net.URI;
import com.qindesign.json.schema.net.URISyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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
import java.util.function.Supplier;
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
      { "https://json-schema.org/draft/2019-09/schema", "/schemas/draft-2019-09/schema.json" },
      { "https://json-schema.org/draft/2019-09/meta/core", "/schemas/draft-2019-09/meta/core.json" },
      { "https://json-schema.org/draft/2019-09/meta/applicator", "/schemas/draft-2019-09/meta/applicator.json" },
      { "https://json-schema.org/draft/2019-09/meta/validation", "/schemas/draft-2019-09/meta/validation.json" },
      { "https://json-schema.org/draft/2019-09/meta/meta-data", "/schemas/draft-2019-09/meta/meta-data.json" },
      { "https://json-schema.org/draft/2019-09/meta/format", "/schemas/draft-2019-09/meta/format.json" },
      { "https://json-schema.org/draft/2019-09/meta/content", "/schemas/draft-2019-09/meta/content.json" },
      { "http://json-schema.org/draft-07/schema", "/schemas/draft-07/schema.json" },
      { "http://json-schema.org/draft-06/schema", "/schemas/draft-06/schema.json" },
      }).collect(Collectors.toUnmodifiableMap(
          data -> URI.parseUnchecked((String) data[0]),
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

  private final ValidatorContext context;

  /**
   * Creates a new validator.
   * <p>
   * Known JSON schemas and resources can be added, however the IDs in the
   * schema will override those resources if there are duplicates. All known IDs
   * and URLs are loaded and scanned so that their IDs can be checked
   * and catalogued.
   * <p>
   * When searching for resources, {@code knownIDs} is searched first and
   * {@code knownURLs} is searched second. Any known URLs having an empty path
   * or a path that ends with a "/" are skipped.
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
   * The following errors will show up in the log, but will not cause a
   * {@link MalformedSchemaException}:
   * <ol>
   * <li>When auto-resolving, base URIs or references that are not also
   *     valid URLs.</li>
   * <li>When auto-resolving, duplicate URLs.</li>
   * <li>Duplicate URIs encountered when loading from the set of known URLs.
   *     Note that duplicate URIs encountered when processing the set of known
   *     IDs are ignored.</li>
   * <li>Errors loading or parsing URLs.</li>
   * </ol>
   *
   * @param schema the schema, must not be {@code null}
   * @param baseURI the schema's base URI, must not be {@code null}
   * @param knownIDs any known JSON schemas, searched first
   * @param knownURLs any known resources, searched second
   * @param options any options
   * @throws MalformedSchemaException if the main schema or any of the other
   *         known schemas is somehow malformed.
   * @throws IllegalArgumentException if the base URI is not absolute or if it
   *         has a non-empty fragment, or if there are any duplicate IDs.
   */
  public Validator(JsonElement schema, URI baseURI,
                   Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                   Options options) throws MalformedSchemaException {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(baseURI, "baseURI");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }
    if (URIs.hasNonEmptyFragment(baseURI)) {
      throw new IllegalArgumentException("baseURI has a non-empty fragment");
    }

    if (options == null) {
      options = new Options();
    }

    // Prepare the main schema
    baseURI = baseURI.normalize();

    // If auto-resolving, then collect new URLs
    Map<URI, URL> autoResolved = null;
    if (options.is(Option.AUTO_RESOLVE)) {
      autoResolved = new HashMap<>();
    }
    Map<URI, Boolean> vocabularies = new HashMap<>();
    Map<URI, Id> ids = prepareSchema(baseURI, schema, options, autoResolved, vocabularies);

    // Prepare all the known schemas
    if (knownIDs != null) {
      for (var e : knownIDs.entrySet()) {
        URI uri = e.getKey().normalize();
        Map<URI, Id> ids2 = prepareSchema(uri, e.getValue(), options, autoResolved, null);
        ids2.forEach(ids::putIfAbsent);
      }
    }

    if (knownURLs == null) {
      knownURLs = new HashMap<>();
    } else {
      knownURLs = new HashMap<>(knownURLs);
    }

    // Add all the auto-resolved values to the set of known URLs
    Set<URL> checkedURLs = new HashSet<>();
    if (autoResolved != null) {
      for (var e : autoResolved.entrySet()) {
        if (knownURLs.putIfAbsent(e.getKey(), e.getValue()) != null) {
          logger.warning("Duplicate URL: " + e.getKey() + ": " + e.getValue());
        }
      }
      autoResolved.clear();
    }

    // Prepare the contents of all known URLs
    // Loop until we've seen all URLs
    do {
      for (var e : knownURLs.entrySet()) {
        URI uri = e.getKey().normalize();
        URL url = e.getValue();
        if (!checkedURLs.add(url)) {
          continue;
        }

        String path = url.getPath();
        if (path.isEmpty() || path.endsWith("/")) {
          continue;
        }

        // First try the original URL
        // Then try the URI itself as a URL, if AUTO_RESOLVE is enabled
        InputStream urlIn = null;
        try {
          urlIn = url.openStream();
          logger.info("Found resource: " + uri + ": " + url);
        } catch (IOException ex) {
          logger.log(Level.WARNING, "Error loading resource: " + uri + ": " + url, ex);

          // When auto-resolving, also check the URI itself
          if (options.is(Option.AUTO_RESOLVE)) {
            try {
              url = uri.toURL();
              urlIn = url.openStream();

              // Replace the URL with the URI-as-URL because it's successful
              e.setValue(url);
              logger.info("Found resource: " + uri + ": " + uri);
            } catch (IllegalArgumentException | MalformedURLException ex2) {
              logger.log(Level.WARNING, "Not a valid resource: " + uri, ex2);
            } catch (IOException ex2) {
              logger.log(Level.WARNING, "Error loading resource: " + uri + ": " + uri, ex2);
            }
          }
        }

        // If there's an input stream, parse the resource
        if (urlIn != null) {
          try (InputStream in = urlIn) {
            try {
              Map<URI, Id> ids2 = prepareSchema(uri, JSON.parse(in), options, autoResolved, null);
              ids2.forEach((uri1, id) -> {
                if (ids.putIfAbsent(uri1, id) != null) {
                  logger.warning("Duplicate URI: " + uri1 + ": from " + id.rootURI);
                }
              });
            } catch (JsonParseException ex) {
              logger.log(Level.SEVERE, "Error parsing resource: " + uri + ": " + url, ex);
            }
          } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error closing resource: " + url, ex);
          }
        }
      }

      if (autoResolved != null) {
        autoResolved.values().removeAll(checkedURLs);
        for (var e : autoResolved.entrySet()) {
          if (knownURLs.putIfAbsent(e.getKey(), e.getValue()) != null) {
            logger.warning("Duplicate URL: " + e.getKey() + ": " + e.getValue());
          }
        }
        if (autoResolved.isEmpty()) {
          break;
        }
      } else {
        break;
      }
    } while (true);

    // Assume all the known specs have been validated
    this.context = new ValidatorContext(baseURI, schema, false,
                                        ids, knownURLs, KNOWN_SCHEMAS, options);

    // Collect the vocabularies from the default schema if the schema is unknown
    if (!vocabularies.isEmpty()) {
      vocabularies.forEach(context::setVocabulary);
    }
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
   * Prepares a schema by scanning and checking it. It is expected that the base
   * URI is already normalized.
   * <p>
   * If auto-resolution of URLs is desired, set {@code knownURLs} to
   * non-{@code null}. Any known URLs having an empty path or a path that ends
   * with a "/" are not accessed.
   * <p>
   * This also validates the schema if it has no root $schema element. During
   * this validation, the vocabularies are optionally collected into
   * {@code vocabularies} if it's non-{@code null}. If there is a root $schema
   * element or if the vocabularies map is {@code null}, then no vocabularies
   * will be collected.
   *
   * @param baseURI the schema's base URI, expected to be normalized
   * @param schema the schema
   * @param options any options
   * @param knownURLs a map into which to put auto-resolved URLs, {@code null}
   *                  to not track auto-resolution
   * @param vocabularies a map into which to put recognized
   *                     vocabularies, optional
   * @return all the known IDs from the schema.
   * @throws MalformedSchemaException if the schema is not valid.
   * @throws NullPointerException if any of the arguments is {@code null}.
   */
  private static Map<URI, Id> prepareSchema(URI baseURI, JsonElement schema, Options options,
                                            Map<URI, URL> knownURLs,
                                            Map<URI, Boolean> vocabularies)
      throws MalformedSchemaException {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(options, "options");

    if (!isSchema(schema)) {
      throw new MalformedSchemaException("not a schema", baseURI);
    }

    // Determine the schema specification
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

    Map<URI, Id> ids = scanIDs(baseURI, schema, spec);

    // Possibly auto-resolve
    if (knownURLs != null) {
      URL url = null;
      try {
        url = baseURI.toURL();
      } catch (IllegalArgumentException | MalformedURLException ex) {
        logger.warning("AUTO_RESOLVE: not a valid base URL: " + baseURI);
      }
      URL baseURL = url;  // So that baseURL is effectively final
      if (baseURL != null) {
        JSON.traverseSchema(baseURI, spec, schema, (e, parent, path, state) -> {
          if (state.isNotKeyword() ||
              !(path.endsWith(CoreRef.NAME) || path.endsWith(CoreRecursiveRef.NAME))) {
            return;
          }

          Supplier<URI> loc =
              () -> state.rootURI().resolve(Strings.jsonPointerToURI(path.toString()));

          if (!JSON.isString(e)) {
            throw new MalformedSchemaException("not a string", loc.get());
          }

          URI ref;
          try {
            ref = URI.parse(e.getAsString());
          } catch (URISyntaxException ex) {
            throw new MalformedSchemaException("not a valid URI", loc.get());
          }

          // Also guess URLs for raw URIs having a scheme or authority
          // Don't need the code below anymore:
//          // Only guess URLs for raw URIs having no scheme and no authority
//          if (ref.scheme() != null || ref.rawAuthority() != null) {
//            return;
//          }

          // Don't guess URLs for URIs that are just fragments
          if (!URIs.isNotFragmentOnly(ref)) {
            return;
          }

          ref = URIs.stripFragment(ref).normalize();
          URI uri = state.baseURI().resolve(ref);

          // Only add if the IDs don't contain this $ref
          // This check is necessary because we don't want to use a URL for
          // known IDs
          if (!ids.containsKey(uri) && !KNOWN_RESOURCES.containsKey(uri)) {
            try {
              knownURLs.putIfAbsent(uri, uri.toURL());
            } catch (MalformedURLException ex) {
              logger.warning("AUTO_RESOLVE: not a valid URL: " + ref);
            }
          }
        });
      }
    }

    // Validate the schema if unknown and collect the vocabularies
    if (isDefaultSpec && schema.isJsonObject()) {
      ValidatorContext context =
          new ValidatorContext(baseURI, schema, true,
                               new HashMap<>(), Collections.emptyMap(), KNOWN_SCHEMAS,
                               new Options().set(Option.FORMAT, false));
      if (!new CoreSchema()
          .apply(new JsonPrimitive(spec.id().toString()), null, schema.getAsJsonObject(),
                 context)) {
        throw new MalformedSchemaException("schema does not validate against " + spec.id(),
                                           baseURI);
      }
      if (vocabularies != null) {
        vocabularies.putAll(context.vocabularies());
      }
    }

    return ids;
  }

  /**
   * Validates an instance against a schema.
   * <p>
   * The annotations and errors are maps from instance locations to an
   * associated {@link Annotation}, with some intervening values. Locations are
   * given as
   * <a href="https://www.rfc-editor.org/rfc/rfc6901.html">JSON Pointers</a>.
   * <ul>
   * <li>The annotations follow this structure: instance location &rarr; name
   *     &rarr; schema location &rarr; {@link Annotation}. The
   *     {@link Annotation} value is dependent on the source.</li>
   * <li>The errors have this structure: instance location &rarr; schema
   *     location &rarr; {@link Error}. The {@link Error} value is dependent on
   *     the source, but is likely to be an informative error message.
   * </ul>
   * <p>
   * If errors are collected, then both valid and invalid results are collected.
   *
   * @param instance the instance, must not be {@code null}
   * @param annotations annotations get stored here, if not {@code null}
   * @param errors errors get stored here, if not {@code null}
   * @return the validation result.
   * @throws NullPointerException if {@code instance} is {@code null}.
   * @throws MalformedSchemaException if the main schema or any of the other
   *         known schemas is somehow malformed.
   */
  public boolean validate(JsonElement instance,
                          Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations,
                          Map<JSONPath, Map<JSONPath, Error<?>>> errors)
      throws MalformedSchemaException
  {
    Objects.requireNonNull(instance, "instance");

    return context.apply(instance, annotations, errors);
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
   * and known. This will return that value, or {@code null} if the value is
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
        URI uri = URI.parse(schemaVal.getAsString());
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
        URI uri = URI.parse(refVal.getAsString());
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
    try {
      // TODO: What base URI to use?
      JSON.traverseSchema(URI.parseUnchecked(""), null, schema, (e, parent, path, state) -> {
        if (!e.isJsonObject()) {
          return;
        }

        e.getAsJsonObject().entrySet().forEach(entry -> {
          // Look into the keyword
          switch (entry.getKey()) {
            case CoreId.NAME:
              if (JSON.isString(entry.getValue())) {
                try {
                  if (URIs.hasNonEmptyFragment(URI.parse(entry.getValue().getAsString()))) {
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
    } catch (MalformedSchemaException ex) {
      throw new RuntimeException(ex);
    }

    if (cantBe.size() >= Specification.values().length) {
      return null;
    }
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
   * The returned map will not contain the given base URI if it's different than
   * the document root ID.
   * <p>
   * The given specification is the one used for processing. It can be
   * identified by the caller via a call to
   * {@link #specificationFromSchema(JsonElement)} and
   * {@link #guessSpecification(JsonElement)}
   *
   * @param baseURI the base URI
   * @param schema the JSON schema
   * @param spec the default specification to use for processing
   * @return a map of IDs to JSON elements.
   * @throws IllegalArgumentException if the base URI has a non-empty fragment.
   * @throws MalformedSchemaException if the schema is considered malformed.
   * @see #specificationFromSchema(JsonElement)
   * @see #guessSpecification(JsonElement)
   */
  public static Map<URI, Id> scanIDs(URI baseURI, JsonElement schema, Specification spec)
      throws MalformedSchemaException {
    if (URIs.hasNonEmptyFragment(baseURI)) {
      throw new IllegalArgumentException("Base URI has a non-empty fragment");
    }
    URI rootURI = URIs.stripFragment(baseURI).normalize();

    Map<URI, Id> ids = new HashMap<>();
    JSON.traverseSchema(rootURI, spec, schema, (e, parent, path, state) -> {
      if (state.isNotSchema() || !e.isJsonObject()) {
        return;
      }

      // Absolute location of this object
      Supplier<URI> loc = () -> state.rootURI().resolve(Strings.jsonPointerToURI(path.toString()));

      // Process any $id
      if (state.hasIDElement()) {
        Id id = new Id(state.baseURI(),
                       state.idElement().getAsString(),
                       state.idURI(),
                       state.baseURIParent(),
                       path,
                       e,
                       state.rootID(),
                       rootURI);

        if (URIs.hasNonEmptyFragment(id.id)) {
          if (ids.put(id.id, id) != null) {
            throw new MalformedSchemaException(
                "anchor not unique: name=" + id.value +
                " base=" + id.base + " rootID=" + id.rootID + " rootURI=" + id.rootURI,
                loc.get());
          }

          // Add the non-anchor part if this isn't only an anchor
          if (URIs.isNotFragmentOnly(state.idURI())) {
            id = new Id(URIs.stripFragment(id.id), id.value, state.idURI(),
                        id.base,
                        id.path, id.element,
                        id.rootID, id.rootURI);
            if (ids.put(id.id, id) != null) {
              throw new MalformedSchemaException("ID not unique", loc.get());
            }
          }
        } else {
          if (ids.put(id.id, id) != null) {
            throw new MalformedSchemaException("ID not unique", loc.get());
          }
        }
      }

      // Process any "$anchor"
      if (state.hasAnchorElement()) {
        URI unresolvedID = URI.parseUnchecked("#" + state.anchorElement().getAsString());
        Id id = new Id(state.baseURI().resolve(unresolvedID),
                       state.anchorElement().getAsString(),
                       unresolvedID,
                       state.baseURI(),
                       path,
                       e,
                       state.rootID(),
                       rootURI);

        if (ids.put(id.id, id) != null) {
          throw new MalformedSchemaException(
              "anchor not unique: name=" + id.value +
              " base=" + id.base + " rootID=" + id.rootID + " rootURI=" + id.rootURI,
              loc.get());
        }
      }
    });

    return ids;
  }
}
