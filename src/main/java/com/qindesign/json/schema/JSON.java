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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
    try (BufferedReader r = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
      return parse(r);
    }
  }

  /**
   * Parses JSON from an {@link InputStream}.
   *
   * @param in the input stream
   * @return the parsed JSON element.
   * @throws IOException if there was a problem reading from the stream.
   * @throws JsonParseException if there was a parsing error.
   */
  public static JsonElement parse(InputStream in) throws IOException {
    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      return parse(r);
    }
  }

  /**
   * Parses JSON from a {@link Reader}.
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
  public static JsonElement parse(Reader r) throws JsonParseException {
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
   * Visitor for JSON tree traversal.
   */
  public interface JsonElementVisitor {
    /**
     * Visits a JSON element. The {@code state} argument holds more information
     * about the element and its position in the tree. It holds information that
     * won't necessarily change for every element.
     *
     * @param e the element being visited
     * @param parent the element's parent
     * @param path the full path to the element, a JSON Pointer
     * @param state holds more information about the element
     */
    void visit(JsonElement e, JsonElement parent, String path, TraverseState state);
  }

  /**
   * Holds state during a JSON tree traversal. It holds information that won't
   * necessarily change for every element.
   */
  public static final class TraverseState {
    /** The current specification, as determined by the latest $schema value. */
    public Specification spec;

    TraverseState copy() {
      TraverseState copy = new TraverseState();
      copy.spec = this.spec;
      return copy;
    }
  }

  /**
   * Traverses a JSON tree and visits each element using {@code visitor}. This
   * uses a preorder ordering.
   *
   * @param e the root of the JSON tree
   * @param visitor the visitor
   */
  public static void traverse(JsonElement e, JsonElementVisitor visitor) {
    traverse(e, null, "", new TraverseState(), visitor);
  }

  /**
   * Recursive method that performs the traversal.
   *
   * @param e the current element
   * @param parent the element's parent
   * @param path the element's full path, a JSON Pointer
   * @param state the tree state
   * @param visitor the visitor
   */
  private static void traverse(JsonElement e, JsonElement parent, String path,
                               TraverseState state,
                               JsonElementVisitor visitor) {
    // Possibly alter the state
    Specification spec = Validator.specificationFromSchema(e);
    if (spec != null) {
      state = state.copy();
      state.spec = spec;
    }

    visitor.visit(e, parent, path, state);

    if (e.isJsonPrimitive() || e.isJsonNull()) {
      return;
    }

    if (e.isJsonArray()) {
      int index = 0;
      for (var item : e.getAsJsonArray()) {
        traverse(item, e, path + "/" + index, state, visitor);
        index++;
      }
      return;
    }

    // Process everything else
    for (var entry : e.getAsJsonObject().entrySet()) {
      String name = entry.getKey();
      name = name.replace("~", "~0");
      name = name.replace("/", "~1");
      traverse(entry.getValue(), e, path + "/" + name, state, visitor);
    }
  }
}
