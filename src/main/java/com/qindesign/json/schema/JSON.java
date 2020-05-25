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
 * Provides JSON tools.
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
}
