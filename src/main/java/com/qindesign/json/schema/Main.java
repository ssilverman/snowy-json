/*
 * Created by shawn on 5/2/20 8:36 AM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    schema = JSON.parse(new File(args[0]));
    instance = JSON.parse(new File(args[1]));
    logger.info("Loaded schema=" + args[0] + " instance=" + args[1]);
    logger.info("Actual spec=" + Validator.specificationFromSchema(schema));
    logger.info("Guessed spec=" + Validator.guessSpecification(schema));

    Options opts = new Options();
    opts.set(Option.FORMAT, true);
    opts.set(Option.CONTENT, true);
    opts.set(Option.DEFAULT_SPECIFICATION, spec);

    Map<String, Map<String, Annotation>> errors = new HashMap<>();

    boolean result = Validator.validate(schema, instance, schemaID,
                                        Collections.emptyMap(), Collections.emptyMap(),
                                        opts, null, errors);
    logger.info("Validation result: " + result);

    JsonWriter w = new JsonWriter(new OutputStreamWriter(System.out));
    w.setIndent("    ");
    Streams.write(basicOutput(result, errors), w);
    w.flush();
  }

  private static JsonObject basicOutput(boolean result,
                                        Map<String, Map<String, Annotation>> errors) {
    JsonObject root = new JsonObject();
    root.add("valid", new JsonPrimitive(result));
    JsonArray errorArr = new JsonArray();
    root.add("errors", errorArr);
    errors.forEach((instanceLoc, map) ->
      map.forEach((schemaLoc, a) -> {
        JsonObject error = new JsonObject();
        error.add("keywordLocation", new JsonPrimitive(a.keywordLocation));
        error.add("absoluteKeywordLocation", new JsonPrimitive(a.absKeywordLocation.toString()));
        error.add("instanceLocation", new JsonPrimitive(a.instanceLocation));

        ValidationResult vr = (ValidationResult) a.value;
        if (vr.result) {
          return;
        }
        error.add("valid", new JsonPrimitive(false));
        if (vr.message == null) {
          error.add(a.name, JsonNull.INSTANCE);
        } else {
          error.add(a.name, new JsonPrimitive(vr.message));
        }
        errorArr.add(error);
      }));
    return root;
  }
}
