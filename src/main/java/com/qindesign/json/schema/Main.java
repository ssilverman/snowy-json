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
import java.util.Collections;
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

    Options opts = new Options();
    boolean result = Validator.validate(schema, instance, schemaID, spec,
                                        Collections.emptyMap(), Collections.emptyMap(),
                                        opts);
    logger.info("Validation result: " + result);
  }
}
