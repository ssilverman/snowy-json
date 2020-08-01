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
 * Created by shawn on 5/16/20 9:45 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import com.qindesign.json.schema.keywords.CoreAnchor;
import com.qindesign.json.schema.keywords.CoreDefs;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.Definitions;
import com.qindesign.json.schema.keywords.Properties;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides JSON tools, including:
 * <ol>
 * <li>Parsing</li>
 * <li>Element checking</li>
 * <li>Tree traversal</li>
 * </ol>
 */
public final class JSON {
  /**
   * Valid anchor pattern.
   *
   * @see <a href="https://www.w3.org/TR/2006/REC-xml-names11-20060816/#NT-NCName">Namespaces in XML 1.1 (Second Edition): NCName</a>
   */
  private static final java.util.regex.Pattern ANCHOR_PATTERN =
      java.util.regex.Pattern.compile("[A-Z_a-z][-A-Z_a-z.0-9]*");

  /**
   * Disallow instantiation.
   */
  private JSON() {
  }

  // According to the source, JsonParser sets lenient mode to true, but I don't want this
  // See: https://github.com/google/gson/issues/1208
  // See: https://stackoverflow.com/questions/43233898/how-to-check-if-json-is-valid-in-java-using-gson/47890960#47890960

  /**
   * Parses JSON from a {@link File}.
   *
   * @param f the file to parse
   * @return the parsed JSON element.
   * @throws IOException if there was a problem reading the file.
   * @throws JsonParseException if there was a parsing error.
   */
  public static JsonElement parse(File f) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
      return parse(in);
    }
  }

  /**
   * Parses JSON from a {@link URL}.
   *
   * @param url the URL whose content to parse
   * @return the parsed JSON element.
   * @throws IOException if there was a problem reading the data.
   * @throws JsonParseException if there was a parsing error.
   */
  public static JsonElement parse(URL url) throws IOException {
    try (InputStream in = new BufferedInputStream(url.openStream())) {
      return parse(in);
    }
  }

  /**
   * Parses JSON from an {@link InputStream}. This decodes the stream using the
   * {@link StandardCharsets#UTF_8} charset. Note that this does not buffer nor
   * close the stream.
   *
   * @param in the input stream
   * @return the parsed JSON element.
   * @throws JsonParseException if there was a parsing error.
   */
  public static JsonElement parse(InputStream in) {
    return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
  }

  /**
   * Parses JSON from a {@link Reader}. Note that this does not buffer nor close
   * the input.
   * <p>
   * This mimics {@link JsonParser#parseReader(Reader)} and
   * {@link JsonParser#parseReader(JsonReader)} for behaviour because
   * {@link JsonParser#parseReader(JsonReader)} forces lenient parsing.
   *
   * @param r the {@link Reader} that reads the JSON content
   * @return the parsed JSON element.
   * @throws JsonParseException if there was an error parsing the document.
   * @see JsonParser#parseReader(Reader)
   * @see JsonParser#parseReader(JsonReader)
   */
  public static JsonElement parse(Reader r) {
    JsonReader jsonReader = new JsonReader(r);

    // Wrap the following code block so we can capture a
    // JsonSyntaxException(MalformedJsonException) and its misleading message
    try {
      JsonElement elem = Streams.parse(jsonReader);
      try {
        if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
          throw new JsonSyntaxException("Expected only one value");
        }
        return elem;
      } catch (MalformedJsonException ex) {
        throw new JsonSyntaxException(ex);
      } catch (IOException ex) {
        throw new JsonIOException(ex);
      } catch (NumberFormatException ex) {
        throw new JsonSyntaxException(ex);
      }
    } catch (JsonSyntaxException ex) {
      // I apologize to everyone reading this code. Gson has a misleading error
      // message otherwise. There doesn't seem to be a clean way out of this
      // "lenient" mire. First, they force lenient mode so I have to call
      // Streams.parse myself, and then this nonsense. The worst that happens
      // here is the exception gets rethrown. Ideally, we shouldn't include the
      // cause because that contains the misleading message, but let's include
      // it anyway because it contains useful stack information.
      if (ex.getCause() instanceof MalformedJsonException) {
        String msg = ex.getCause().getMessage();
        final String prefix = "Use JsonReader.setLenient(true) to accept malformed JSON ";
        if (msg != null && msg.startsWith(prefix)) {
          ex = new JsonSyntaxException(msg.substring(prefix.length()), ex.getCause());
        }
      }
      throw ex;
    }
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
   * Convenience method to get a string-valued member of a JSON object. This
   * will return {@code null} if the element is not an object or if there is no
   * string-valued member having the given name.
   *
   * @param e the element from which to retrieve the member
   * @param name the member name
   * @return the member value, or {@code null} if there is no such member.
   */
  public static String getStringMember(JsonElement e, String name) {
    if (e.isJsonObject()) {
      JsonElement titleElem = e.getAsJsonObject().get(name);
      if (titleElem != null && isString(titleElem)) {
        return titleElem.getAsString();
      }
    }
    return null;
  }

  /**
   * Visitor for schema tree traversal.
   */
  public interface SchemaVisitor {
    /**
     * Visits a schema element. The {@code state} argument holds more
     * information about the element and its position in the tree. It holds
     * information that won't necessarily change for every element.
     *
     * @param e the element being visited
     * @param parent the element's parent, may be {@code null}
     * @param path the full path to the element, a list of path elements
     * @param state holds more information about the element
     * @throws MalformedSchemaException if there was a problem with the schema.
     */
    void visit(JsonElement e, JsonElement parent, JSONPath path, SchemaTraverseState state)
        throws MalformedSchemaException;
  }

  /**
   * Visitor for tree traversal.
   */
  public interface Visitor {
    /**
     * Visits a JSON element.
     *
     * @param e the element being visited
     * @param parent the element's parent, may be {@code null}
     * @param path the full path to the element, a list of path elements
     */
    void visit(JsonElement e, JsonElement parent, JSONPath path);
  }

  /**
   * Holds state during a schema tree traversal. It holds information that won't
   * necessarily change for every element.
   */
  public static final class SchemaTraverseState {
    /** The current specification, as determined by the latest $schema value. */
    Specification spec;

    // Whether the object is a "properties" or a definitions
    boolean inProperties;
    boolean inDefs;

    boolean isNotKeyword;

    /** Whether the current element is definitely not a potential schema. */
    boolean isNotSchema;

    // ID tracking
    URI rootURI;             // The original base URI
    URI rootID;              // The root ID, if it exists
    URI base;                // The current base URI
    URI id;                  // The normalized $id value, if it exists
    URI baseParent;          // The parent of the current base, if it exists
    JSONPath pathFromBase;   // The path from the current base to the current
    JsonElement idElem;      // The $id element, if it exists
    JsonElement anchorElem;  // The $anchor element, if it exists

    /**
     * Default constructor.
     */
    SchemaTraverseState() {
    }

    /**
     * Returns the specification. This may return {@code null} if it is unknown.
     *
     * @return the specification, or {@code null} if unknown.
     */
    public Specification spec() {
      return spec;
    }

    /**
     * Returns whether the current element is the contents of a proper
     * "properties" keyword. An element is not considered a "properties" value
     * if its parent is the contents of a "properties" or definitions keyword.
     *
     * @return whether the element is the contents of a proper "properties".
     */
    public boolean isProperties() {
      return inProperties;
    }

    /**
     * Returns whether the current element is the contents of a proper
     * definitions keyword. An element is not considered a definitions value if
     * its parent is the contents of a "properties" or definitions keyword.
     *
     * @return whether the element is the contents of a proper definitions.
     */
    public boolean isDefs() {
      return inDefs;
    }

    /**
     * Returns whether the element is definitely not a schema. If this returns
     * {@code false}, then the element is only possibly a schema. The contents
     * of proper "properties" and definitions are not schemas.
     * <p>
     * This will only return {@code true} for anything that definitely is not a
     * schema. This only looks at the element name and parent, and says nothing
     * about the element type or contents. For example, this may return
     * {@code false} if the element is not a Boolean and not an object, but has
     * a "properties" or definitions parent.
     *
     * @return whether the element is definitely not a schema.
     */
    public boolean isNotSchema() {
      return isNotSchema;
    }

    /**
     * Returns whether the element is definitely not the contents of a keyword.
     * If this returns {@code false}, then the element only possibly belongs to
     * a keyword. Elements having a proper "properties" or definitions parent
     * can't be keywords.
     * <p>
     * This will only return {@code true} for anything that definitely is not a
     * keyword. This only looks at the parents and says nothing about the
     * element or parent type or contents. For example, this will return
     * {@code false} if the parent is an array and is not also a "properties"
     * or definitions.
     *
     * @return whether the element is definitely not a keyword.
     */
    public boolean isNotKeyword() {
      return isNotKeyword;
    }

    /**
     * Returns the document root ID, if detected. This may be {@code null} if
     * there is no root ID. This will be normalized.
     * <p>
     * This will be the same as the $id of the root object, if it exists.
     * <p>
     * Any empty fragment will have been removed.
     *
     * @return the document root URI, may be {@code null}.
     */
    public URI rootID() {
      return rootID;
    }

    /**
     * Returns the initial base URI of the schema. This is likely the URI that
     * was used to retrieve the document, but may also be something else. This
     * will be normalized.
     * <p>
     * Any fragment will have been removed.
     *
     * @return the initial schema base URI.
     */
    public URI rootURI() {
      return rootURI;
    }

    /**
     * Returns the current base URI. This is the value of the latest $id
     * resolved against its parent base URI. This will be normalized.
     * <p>
     * Any empty fragment will have been removed.
     *
     * @return the current base URI.
     */
    public URI baseURI() {
      return base;
    }

    /**
     * Returns the unresolved and normalized URI for the current object's $id.
     * This will return {@code null} if the current element is not an object or
     * if the object does not contain an $id element.
     * <p>
     * Any empty fragment will have been removed.
     *
     * @return the current $id value, or {@code null} if there's no $id in the
     *         current object.
     */
    public URI idURI() {
      return id;
    }

    /**
     * Returns the base URI used to resolve the current base URI. This may be
     * {@code null} null.
     * <p>
     * Any empty fragment will have been removed.
     *
     * @return the base used to resolve the current base, may be {@code null}.
     */
    public URI baseURIParent() {
      return baseParent;
    }

    /**
     * Returns the path relative to the current base URI. If there is no base
     * URI, then this returns the path relative to the root.
     *
     * @return the path relative to the current base URI, or relative to the
     *         root if there is no current base URI.
     */
    public JSONPath pathFromBase() {
      return pathFromBase;
    }

    /**
     * Returns whether the current element is an object and contains a valid
     * "$id" member.
     *
     * @return whether the current object contains a valid "$id".
     */
    public boolean hasIDElement() {
      return idElem != null;
    }

    /**
     * Returns the "$id" element if the current element is an object and has a
     * valid "$id" member. Otherwise, this returns {@code null}.
     *
     * @return the "$id" element of the current object, or {@code null} if there
     *         is no such member or if the current element is not an object.
     */
    public JsonElement idElement() {
      return idElem;
    }

    /**
     * Returns whether the current element is an object and contains a valid
     * "$anchor" member. This will return {@code false} if the current
     * specification does not support this keyword.
     *
     * @return whether the current element contains a valid "$anchor", or
     *         {@code false} if the current specification does not support
     *         this keyword.
     */
    public boolean hasAnchorElement() {
      return anchorElem != null;
    }

    /**
     * Returns the "$anchor" element if the current element is an object and has
     * a valid "$anchor" member. Otherwise, this returns {@code null}. This will
     * also return {@code null} if the current specification does not support
     * this keyword.
     *
     * @return the "$anchor" element of the current object, or {@code null} if
     *         there is no such member, if the current element is not an object,
     *         or if the current specification does not support this keyword.
     */
    public JsonElement anchorElement() {
      return anchorElem;
    }
  }

  /**
   * Traverses a JSON schema and visits each element using {@code visitor}. This
   * uses a preorder ordering.
   * <p>
   * The initial base URI is set to the given base URI after removing any
   * fragment and after normalization. The optional default specification will
   * be used in the case that one could not be determined.
   * <p>
   * This returns any found root ID, or {@code null} if one is not found.
   *
   * @param baseURI a non-optional initial base URI
   * @param defaultSpec the optional default specification to use
   * @param schema the root of the JSON schema tree
   * @param visitor the visitor
   * @return the root ID if one is found, otherwise {@code null}.
   * @throws MalformedSchemaException if there was a problem with the schema.
   */
  public static URI traverseSchema(URI baseURI, Specification defaultSpec, JsonElement schema,
                                   SchemaVisitor visitor) throws MalformedSchemaException {
    Objects.requireNonNull(baseURI, "baseURI");

    SchemaTraverseState state = new SchemaTraverseState();
    state.spec = defaultSpec;
    state.base = URIs.stripFragment(baseURI).normalize();
    state.rootURI = state.base;
    return traverseSchema(schema, null, JSONPath.absolute(), state, visitor);
  }

  /**
   * Recursive method that performs the schema traversal. This returns any
   * discovered root ID.
   *
   * @param e the current element
   * @param parent the element's parent
   * @param path the element's full path
   * @param state the tree state
   * @param visitor the visitor
   * @return the root ID if one is found, otherwise {@code null}.
   * @throws MalformedSchemaException if there was a problem with the schema, as
   *         thrown by the visitor.
   */
  private static URI traverseSchema(JsonElement e, JsonElement parent, JSONPath path,
                                    SchemaTraverseState state,
                                    SchemaVisitor visitor) throws MalformedSchemaException {
    SchemaTraverseState oldState = state;
    state = new SchemaTraverseState();
    state.spec = oldState.spec;
    state.rootURI = oldState.rootURI;
    state.rootID = oldState.rootID;
    state.base = oldState.base;
    state.baseParent = oldState.baseParent;
    state.pathFromBase = oldState.pathFromBase;

    // If we're inside a "properties" or a definitions, then the contents of any
    // member can be a schema and the member can't be a keyword
    state.inProperties = false;
    state.inDefs = false;
    state.isNotSchema = false;
    state.isNotKeyword = oldState.inProperties || oldState.inDefs;
    if (!state.isNotKeyword) {
      if (path.endsWith(Properties.NAME)) {
        state.inProperties = true;
        state.isNotSchema = true;
      } else if (path.endsWith(CoreDefs.NAME)) {
        if (state.spec == null || state.spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          state.inDefs = true;
          state.isNotSchema = true;
        }
      } else if (path.endsWith(Definitions.NAME)) {
        if (state.spec == null || state.spec.ordinal() < Specification.DRAFT_2019_09.ordinal()) {
          state.inDefs = true;
          state.isNotSchema = true;
        }
      }
    }

    // Track specification
    if (!state.isNotSchema) {
      Specification spec = Validator.specificationFromSchema(e);
      if (spec != null) {
        state.spec = spec;
      }
    }

    // Find and process any ID and anchor
    state.idElem = null;
    state.anchorElem = null;
    state.id = null;
    if (parent == null) {
      state.pathFromBase = JSONPath.absolute();
    } else {
      state.pathFromBase = state.pathFromBase.append(path.get(path.size() - 1));
    }

    if (!state.isNotSchema && e.isJsonObject()) {
      // ID element
      JsonElement idElem = e.getAsJsonObject().get(CoreId.NAME);
      if (idElem != null) {
        // Absolute location of the ID element
        Supplier<URI> loc = () ->
            oldState.rootURI
                .resolve(Strings.jsonPointerToURI(path.append(CoreId.NAME).toString()));

        if (!isString(idElem)) {
          throw new MalformedSchemaException("not a string", loc.get());
        }
        try {
          URI unresolvedID = URI.parse(idElem.getAsString()).normalize();
          URI id = state.base.resolve(unresolvedID).normalize();

          if (URIs.hasNonEmptyFragment(id)) {
            // Draft 2019-09 and later can't have anchors
            if (state.spec != null &&
                state.spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
              throw new MalformedSchemaException("has a non-empty fragment", loc.get());
            }

            // TODO: Should we use the non-raw fragment here?
            if (!ANCHOR_PATTERN.matcher(id.fragment()).matches()) {
              throw new MalformedSchemaException("invalid plain name", loc.get());
            }
          } else {
            id = URIs.stripFragment(id);
          }

          state.idElem = idElem;
          state.baseParent = state.base;
          state.base = id;
          state.id = unresolvedID;
          state.pathFromBase = JSONPath.absolute();
          if (parent == null) {
            state.rootID = id;
          }
        } catch (URISyntaxException ex) {
          throw new MalformedSchemaException("not a valid URI-reference", loc.get());
        }
      }

      // Anchor element
      if (state.spec != null && state.spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        JsonElement anchorElem = e.getAsJsonObject().get(CoreAnchor.NAME);
        if (anchorElem != null) {
          // Absolute location of the anchor element
          Supplier<URI> loc = () ->
              oldState.rootURI
                  .resolve(Strings.jsonPointerToURI(path.append(CoreAnchor.NAME).toString()));

          if (!isString(anchorElem)) {
            throw new MalformedSchemaException("not a string", loc.get());
          }
          if (!ANCHOR_PATTERN.matcher(anchorElem.getAsString()).matches()) {
            throw new MalformedSchemaException("invalid plain name", loc.get());
          }
          state.anchorElem = anchorElem;
        }
      }
    }

    visitor.visit(e, parent, path, state);

    if (e.isJsonPrimitive() || e.isJsonNull()) {
      return state.rootID;
    }

    if (e.isJsonArray()) {
      int index = 0;
      for (var item : e.getAsJsonArray()) {
        traverseSchema(item, e, path.append(Integer.toString(index)), state, visitor);
        index++;
      }
    } else {
      // Process everything else
      for (var entry : e.getAsJsonObject().entrySet()) {
        traverseSchema(entry.getValue(), e, path.append(entry.getKey()), state, visitor);
      }
    }

    return state.rootID;
  }

  /**
   * Traverses a JSON tree and visits each element using {@code visitor}. This
   * uses a preorder ordering.
   *
   * @param e the root of the JSON tree
   * @param visitor the visitor
   */
  public static void traverse(JsonElement e, Visitor visitor) {
    traverse(e, null, JSONPath.absolute(), visitor);
  }

  /**
   * Recursive method that performs the tree traversal.
   *
   * @param e the current element
   * @param parent the element's parent
   * @param path the element's full path
   * @param visitor the visitor
   */
  private static void traverse(JsonElement e, JsonElement parent, JSONPath path,
                               Visitor visitor) {
    visitor.visit(e, parent, path);

    if (e.isJsonPrimitive() || e.isJsonNull()) {
      return;
    }

    if (e.isJsonArray()) {
      int index = 0;
      for (var item : e.getAsJsonArray()) {
        traverse(item, e, path.append(Integer.toString(index)), visitor);
        index++;
      }
    } else {
      // Process everything else
      for (var entry : e.getAsJsonObject().entrySet()) {
        traverse(entry.getValue(), e, path.append(entry.getKey()), visitor);
      }
    }
  }
}
