/*
 * Created by shawn on 4/22/20 10:38 AM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.keywords.CoreSchema;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validator tools.
 */
public class Validator {
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
      }).collect(Collectors.toMap(
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

  /**
   * @see <a href="https://www.w3.org/TR/2006/REC-xml-names11-20060816/#NT-NCName">Namespaces in XML 1.1 (Second Edition): NCName</a>
   */
  public static final java.util.regex.Pattern ANCHOR_PATTERN =
      java.util.regex.Pattern.compile("[A-Z_a-z][-A-Z_a-z.0-9]*");

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
   * @param defaultSpec the default specification to use
   * @param knownIDs any known JSON contents, searched first
   * @param knownURLs any known resources, searched second
   * @return the validation result.
   * @throws MalformedSchemaException if the schema is somehow malformed.
   */
  public static boolean validate(JsonElement schema, JsonElement instance,
                                 URI baseURI, Specification defaultSpec,
                                 Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs)
      throws MalformedSchemaException
  {
    Specification spec = determineSpecification(schema, defaultSpec);
    var ids = scanIDs(baseURI, schema, spec);
    if (knownIDs != null) {
      knownIDs.forEach((uri, e) -> ids.putIfAbsent(new Id(uri), e));
    }
    if (knownURLs == null) {
      knownURLs = Collections.emptyMap();
    }
    ValidatorContext context =
        new ValidatorContext(baseURI, spec, ids, knownURLs, Collections.emptySet());
    return context.apply(schema, "", instance, "");
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
        return Main.parse(in);
      } catch (JsonParseException ex) {
        logger.log(Level.SEVERE, "Error parsing resource: " + uri, ex);
      }
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error loading resource: " + uri, ex);
    }
    return null;
  }

  /**
   * Determines the specification of a schema. The heuristics are:
   * <ul>
   * <li>Examine any $schema keyword to see if the value is valid and known</li>
   * </ul>
   * <p>
   * This returns {@code defaultSpec} if a specification could not otherwise
   * be identified.
   *
   * @param schema the schema object
   * @param defaultSpec the spec to be returned as a default
   * @return the specification or the default if one could not be determined.
   */
  public static Specification determineSpecification(JsonElement schema,
                                                     Specification defaultSpec) {
    if (!schema.isJsonObject()) {
      return defaultSpec;
    }
    JsonElement schemaVal = schema.getAsJsonObject().get(CoreSchema.NAME);
    if (schemaVal == null || !isString(schemaVal)) {
      return defaultSpec;
    }
    try {
      URI uri = new URI(schemaVal.getAsString());
      if (uri.isAbsolute() && uri.normalize().equals(uri)) {
        return Specification.of(URIs.stripFragment(uri));
      }
    } catch (URISyntaxException ex) {
      // Ignore
    }
    return defaultSpec;
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
   * {@link #determineSpecification(JsonElement, Specification)}.
   *
   * @param baseURI the base URI
   * @param e the JSON document
   * @param spec the specification to use for processing
   * @return a map of IDs to JSON elements.
   * @throws IllegalArgumentException if the base URI has a non-empty fragment.
   * @throws MalformedSchemaException if the schema is considered malformed.
   * @see #determineSpecification(JsonElement, Specification)
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
