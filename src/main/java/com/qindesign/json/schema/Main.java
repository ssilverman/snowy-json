/*
 * Created by shawn on 5/2/20 8:36 AM.
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
  private static final Class<?> CLASS = Main.class;

  static {
    System.setProperty(
        "java.util.logging.SimpleFormatter.format",
        "%3$s: %1$tc [%4$s] %5$s%6$s%n");
  }

  private static final Level loggingLevel = Level.CONFIG;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  static {
    // Don't do it this way because that affects the global logger:
//    Logger logger = LogManager.getLogManager().getLogger("");
//    logger.setLevel(Level.CONFIG);
//    for (Handler h : logger.getHandlers()) {
//      h.setLevel(Level.CONFIG);
//    }

    Logger parentLogger = logger.getParent();
    parentLogger.setUseParentHandlers(false);
    parentLogger.setLevel(loggingLevel);
    Handler[] handlers = parentLogger.getHandlers();
    if (handlers.length == 0) {
      Handler h = new ConsoleHandler();
      h.setLevel(loggingLevel);
      parentLogger.addHandler(h);
    } else {
      for (Handler h : handlers) {
        h.setLevel(loggingLevel);
      }
    }
  }

  private static final Specification spec = Specification.DRAFT_2019_09;

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out.println("Usage: " + CLASS.getName() + " <schema> <instance>");
      System.exit(1);
      return;
    }

    URI schemaID = new File(args[0]).toURI();
    JsonElement schema;
    JsonElement instance;

    // Load the schema and instance
    schema = parse(new File(args[0]));
    instance = parse(new File(args[1]));
    logger.info("Loaded schema=" + args[0] + " instance=" + args[1]);

    boolean result = Validator.validate(schema, instance, schemaID, spec);
    logger.info("Validation result: " + result);
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
}
