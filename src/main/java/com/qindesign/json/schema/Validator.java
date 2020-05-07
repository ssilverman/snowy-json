/*
 * Created by shawn on 4/22/20 10:38 AM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Validator {
  private static final Class<?> CLASS = Validator.class;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  // https://www.baeldung.com/java-initialize-hashmap
  private static final Map<URI, String> KNOWN_RESOURCES = Stream.of(new Object[][] {
      { "https://json-schema.org/draft/2019-09/schema", "/draft-2019-09/schema.json" },
      { "https://json-schema.org/draft/2019-09/meta/core", "/draft-2019-09/core.json" },
      { "https://json-schema.org/draft/2019-09/meta/applicator", "/draft-2019-09/applicator.json" },
      { "https://json-schema.org/draft/2019-09/meta/validation", "/draft-2019-09/validation.json" },
      { "https://json-schema.org/draft/2019-09/meta/meta-data", "/draft-2019-09/meta-data.json" },
      { "https://json-schema.org/draft/2019-09/meta/format", "/draft-2019-09/format.json" },
      { "https://json-schema.org/draft/2019-09/meta/content", "/draft-2019-09/content.json" },
      }).collect(Collectors.toMap(data -> URI.create((String) data[0]), data -> (String) data[1]));

  /**
   * @see <a href="https://www.w3.org/TR/2006/REC-xml-names11-20060816/#NT-NCName">Namespaces in XML 1.1 (Second Edition): NCName</a>
   */
  private static final java.util.regex.Pattern ANCHOR_PATTERN =
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

  public static boolean validate(JsonElement schema, JsonElement instance, URI baseURI)
      throws MalformedSchemaException
  {
    return new ValidatorContext(baseURI, scanIDs(baseURI, schema)).apply(schema.getAsJsonObject(), "", instance, "");
  }

  /**
   * Follows a JSON pointer into a JSON element and returns the requested
   * sub-element. It is expected that {@code ptr} is a valid JSON pointer.
   *
   * @param e the element to traverse
   * @param ptr the JSON pointer
   * @return the specified sub-element or {@code null} if not found.
   */
  public static JsonElement followPointer(JsonElement e, String ptr) {
    boolean first = true;
    // Split using a negative limit so that trailing empty strings are allowed
    for (String part : ptr.split("/", -1)) {
      // Only ignore the first empty string, the one before the initial "/"
      // All others could be zero-length member names
      if (first) {
        first = false;
        if (part.isEmpty()) {
          continue;
        }
      }

      if (e == null) {
        return null;
      }
      try {
        int index = Integer.parseInt(part);
        if (!e.isJsonArray()) {
          return null;
        }
        if (index >= e.getAsJsonArray().size()) {
          return null;
        }
        e = e.getAsJsonArray().get(index);
        continue;
      } catch (NumberFormatException ex) {
        // Nothing, skip to name processing
      }

      if (!e.isJsonObject()) {
        return null;
      }

      // Transform the part
      part = part.replace("~0", "~");
      part = part.replace("~1", "/");
      e = e.getAsJsonObject().get(part);
    }
    return e;
  }

  /**
   * Loads a resource as JSON. This returns {@code null} if the resource could
   * not be found.
   *
   * @param uri the resource ID
   * @return the resource, or {@code null} if the resource could not be found.
   */
  public static JsonElement loadResource(URI uri) {
    String path = KNOWN_RESOURCES.get(uri);
    if (path == null) {
      return null;
    }
    try (InputStream in = Validator.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        return null;
      }
      try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        return Main.parse(r);
      } catch (JsonParseException ex) {
        logger.log(Level.SEVERE, "Error parsing resource: " + uri, ex);
      }
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error loading resource: " + uri, ex);
    }
    return null;
  }

  /**
   * Scans all the IDs in a JSON document starting from a given base URI. The
   * base URI is the initial known document resource ID. It will be normalized.
   * <p>
   * This will return at least one ID mapping to the base element, which may
   * be redefined by an ID element.
   *
   * @param baseURI the base URI
   * @param e the JSON document
   * @return a map of IDs to JSON elements.
   * @throws IllegalArgumentException if the base URI has a non-empty fragment.
   * @throws MalformedSchemaException if the schema is considered malformed.
   */
  public static Map<Id, JsonElement> scanIDs(URI baseURI, JsonElement e)
      throws MalformedSchemaException {
    if (baseURI.getRawFragment() != null && !baseURI.getRawFragment().isEmpty()) {
      throw new IllegalArgumentException("Base UI with non-empty fragment");
    }
    baseURI = baseURI.normalize();

    Map<Id, JsonElement> ids = new HashMap<>();
    Id id = new Id(baseURI);
    id.value = null;
    id.base = null;
    id.path = "";
    id.root = baseURI;
    id.rootURI = baseURI;
    ids.put(id, e);
    // TODO: Use the return value
    scanIDs(baseURI, baseURI, baseURI, null, "", null, e, ids);
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
   * @return the root ID, may change from the original.
   * @throws MalformedSchemaException if the schema is considered malformed
   *         vis-à-vis IDs.
   */
  private static URI scanIDs(URI rootURI, URI rootID, URI baseURI,
                             URI parentID, String name,
                             JsonElement parent, JsonElement e,
                             Map<Id, JsonElement> ids)
      throws MalformedSchemaException {
    if (e.isJsonPrimitive()) {
      return rootID;
    }

    // Create the parent ID of the processed sub-elements
    URI newParentID;
    if (parent == null) {
      newParentID = URI.create("");
    } else {
      name = name.replace("~", "~0");
      name = name.replace("/", "~1");
      newParentID = URI.create(parentID.getPath() + "/" + name);
    }

    if (e.isJsonArray()) {
      int index = 0;
      for (var elem : e.getAsJsonArray()) {
        scanIDs(rootURI, rootID, baseURI, newParentID, Integer.toString(index++), e, elem, ids);
      }
      return rootID;
    }

    // Process any "$id"
    JsonElement value = e.getAsJsonObject().get("$id");
    if (value != null) {
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new MalformedSchemaException("not a string", newParentID);
      }

      URI uri;
      try {
        uri = URI.create(value.getAsString()).normalize();
      } catch (IllegalArgumentException ex) {
        throw new MalformedSchemaException("not a valid URI-reference", newParentID);
      }
      if (uri.getRawFragment() != null && !uri.getRawFragment().isEmpty()) {
        throw new MalformedSchemaException("has a non-empty fragment", newParentID);
      }

      Id id = new Id(baseURI.resolve(uri));
      id.value = value.getAsString();
      id.base = baseURI;
      id.path = newParentID.getPath();
      id.root = rootID;
      id.rootURI = rootURI;
      if (ids.put(id, e) != null) {
        throw new MalformedSchemaException("ID not unique", newParentID);
      }

      baseURI = id.id;
      if (parent == null) {
        rootID = id.id;
      }
    }

    // Process any "$anchor"
    value = e.getAsJsonObject().get("$anchor");
    if (value != null) {
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new MalformedSchemaException("not a string", newParentID);
      }
      if (!ANCHOR_PATTERN.matcher(value.getAsString()).matches()) {
        throw new MalformedSchemaException("invalid plain name", newParentID);
      }

      Id id = new Id(baseURI.resolve("#" + value.getAsString()));  // Normalize?
      id.value = value.getAsString();
      id.base = baseURI;
      id.path = newParentID.getPath();
      id.root = rootID;
      id.rootURI = rootURI;
      if (ids.put(id, e) != null) {
        throw new MalformedSchemaException(
            "anchor not unique: name=" + id.value +
            " base=" + id.base + " rootID=" + id.root + " rootURI=" + id.rootURI,
            newParentID);
      }
    }

    // Process everything else
    for (var entry : e.getAsJsonObject().entrySet()) {
      if (entry.getKey().equals("$id") || entry.getKey().equals("$anchor")) {
        continue;
      }
      scanIDs(rootURI, rootID, baseURI, newParentID, entry.getKey(), e, entry.getValue(), ids);
    }

    return rootID;
  }
}
