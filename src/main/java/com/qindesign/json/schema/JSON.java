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
    try {
      JsonReader jsonReader = new JsonReader(r);
      JsonElement e = Streams.parse(jsonReader);
      if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
        throw new JsonSyntaxException("Expected only one value");
      }
      return e;
    } catch (MalformedJsonException e) {
      throw new JsonSyntaxException(e);
    } catch (IOException e) {
      throw new JsonIOException(e);
    } catch (NumberFormatException e) {
      throw new JsonSyntaxException(e);
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
     */
    void visit(JsonElement e, JsonElement parent, JSONPath path, SchemaTraverseState state);
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
    URI baseURI;
    JSONPath pathFromBase;
    boolean hasID;
    boolean isIDMalformed;

    /**
     * Default constructor.
     */
    SchemaTraverseState() {
    }

    /**
     * Copy constructor.
     *
     * @param state the state to copy
     */
    SchemaTraverseState(SchemaTraverseState state) {
      this.spec = state.spec;
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
     * {@code false} then the element is only possibly a schema. The contents
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
     * If this returns {@code false} then the element only possibly belongs to
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
      return isNotSchema;
    }

    /**
     * Returns the current base URI. This may be {@code null} if the initial
     * base URI was set to {@code null} and there's no intervening ID. This will
     * not necessarily be normalized.
     *
     * @return the current base URI, may be {@code null}.
     */
    public URI baseURI() {
      return baseURI;
    }

    /**
     * Returns the path relative to the current base URI. If there is no base
     * URI then this returns the path relative to the root.
     *
     * @return the path relative to the current base URI, or relative to the
     *         root if there is no current base URI.
     */
    public JSONPath pathFromBase() {
      return pathFromBase;
    }

    /**
     * Returns whether the current element is an object and contains an
     * "$id" member.
     *
     * @return whether the current element contains an "$id".
     */
    public boolean hasID() {
      return hasID;
    }

    /**
     * Returns whether the current element contains an "$id" and if the value
     * is malformed.
     *
     * @return
     */
    public boolean isIDMalformed() {
      return isIDMalformed;
    }
  }

  /**
   * Traverses a JSON schema and visits each element using {@code visitor}. This
   * uses a preorder ordering.
   * <p>
   * The initial base URI is set to the given base URI after removing any
   * fragment and after normalization. The default specification will be used in
   * the case that one could not be determined. Both are optional.
   *
   * @param baseURI an optional initial base URI
   * @param spec the optional default specification to use
   * @param e the root of the JSON tree
   * @param visitor the visitor
   */
  public static void traverseSchema(URI baseURI, Specification spec, JsonElement e, SchemaVisitor visitor) {
    SchemaTraverseState state = new SchemaTraverseState();
    if (baseURI != null) {
      state.baseURI = URIs.stripFragment(baseURI).normalize();
    }
    state.spec = spec;
    traverseSchema(e, null, JSONPath.absolute(), state, visitor);
  }

  /**
   * Recursive method that performs the schema traversal.
   *
   * @param e the current element
   * @param parent the element's parent
   * @param path the element's full path
   * @param state the tree state
   * @param visitor the visitor
   */
  private static void traverseSchema(JsonElement e, JsonElement parent, JSONPath path,
                                     SchemaTraverseState state,
                                     SchemaVisitor visitor) {
    SchemaTraverseState oldState = state;
    state = new SchemaTraverseState();
    state.spec = oldState.spec;
    state.baseURI = oldState.baseURI;
    state.pathFromBase = oldState.pathFromBase;

    // If we're inside a "properties" or a definitions then the contents of any
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
    if (!state.isNotKeyword) {
      Specification spec = Validator.specificationFromSchema(e);
      if (spec != null) {
        state = new SchemaTraverseState(state);
        state.spec = spec;
      }
    }

    // Find any ID
    state.hasID = false;
    state.isIDMalformed = false;
    if (parent == null) {
      state.pathFromBase = JSONPath.absolute();
    } else {
      state.pathFromBase = state.pathFromBase.append(path.get(path.size() - 1));
    }
    if (!state.isNotSchema && e.isJsonObject()) {
      JsonElement idElem = e.getAsJsonObject().get(CoreId.NAME);
      if (idElem != null && isString(idElem)) {
        state.hasID = true;
        try {
          state.baseURI = URI.parse(idElem.getAsString());
          state.pathFromBase = JSONPath.absolute();
        } catch (URISyntaxException ex) {
          state.isIDMalformed = true;
        }
      }
    }

    visitor.visit(e, parent, path, state);

    if (e.isJsonPrimitive() || e.isJsonNull()) {
      return;
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
