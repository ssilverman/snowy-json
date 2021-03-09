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
 * Created by shawn on 5/2/20 8:36 AM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.qindesign.json.schema.util.Logging;
import com.qindesign.json.schema.net.URI;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A rudimentary schema coverage checker. This is an example program.
 * <p>
 * This program takes two arguments:
 * <ol>
 * <li>Schema path or URL</li>
 * <li>Instance path or URL</li>
 * </ol>
 */
public class Main {
  private static final Class<?> CLASS = Main.class;

  /**
   * Disallow instantiation.
   */
  private Main() {
  }

  private static final Level loggingLevel = Level.CONFIG;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  static {
    Logging.init(logger, loggingLevel);
  }

  /** The default specification. */
  private static final Specification spec = Specification.DRAFT_2019_09;

  /**
   * Main program entry point.
   *
   * @param args the program arguments
   * @throws IOException if there was an error reading the files.
   * @throws JsonParseException if there was an error parsing the JSON.
   * @throws MalformedSchemaException if there was a problem with the schema.
   */
  public static void main(String[] args) throws IOException, MalformedSchemaException {
    if (args.length != 2) {
      System.out.println("Usage: " + CLASS.getName() + " <schema> <instance>");
      System.exit(1);
      return;
    }

    URI schemaID = new URI(new File(args[0]).toURI());
    JsonElement schema;
    JsonElement instance;

    // Load the schema and instance
    // First try them as a URL
    try {
      schema = getFromURL(args[0], "Schema");
    } catch (MalformedURLException ex) {
      schema = JSON.parse(new File(args[0]));
    }
    try {
      instance = getFromURL(args[1], "Instance");
    } catch (MalformedURLException ex) {
      instance = JSON.parse(new File(args[1]));
    }
    logger.info("Loaded schema=" + args[0] + " instance=" + args[1]);
    logger.info("Actual spec=" + Validator.specificationFromSchema(schema));
    logger.info("Guessed spec=" + Validator.guessSpecification(schema));

    Options opts = new Options();
    opts.set(Option.FORMAT, true);
    opts.set(Option.CONTENT, true);
    opts.set(Option.DEFAULT_SPECIFICATION, spec);
    // Uncomment to do auto-resolution
//    opts.set(Option.AUTO_RESOLVE, true);
    // Uncomment to collect annotations for failed schemas
//    opts.set(Option.COLLECT_ANNOTATIONS_FOR_FAILED, true);

    Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations = new HashMap<>();
    Map<JSONPath, Map<JSONPath, Error<?>>> errors = new HashMap<>();

    long time = System.currentTimeMillis();
    Validator validator = new Validator(schema, schemaID, null, null, opts);
    boolean result = validator.validate(instance, annotations, errors);
    time = System.currentTimeMillis() - time;
    logger.info("Validation result: " + result + " (" + time/1000.0 + "s)");

    // Basic output
    System.out.println("Basic output:");
    JSON.print(System.out, basicOutput(result, errors), "    ");
    System.out.println();

    // Annotations
    System.out.println();
    System.out.println("Annotations:");
    JSON.print(System.out, annotationOutput(annotations), "    ");
    System.out.println();
  }

  /**
   * Gets a JSON object from a potential URL and prints the content type.
   *
   * @param spec the URL spec
   * @param name the name to use for the logging
   * @return the parsed JSON.
   * @throws MalformedURLException if the spec is not a valid URL.
   * @throws IOException if there was an error reading from the resource.
   * @throws JsonParseException if there was a JSON parsing error.
   */
  private static JsonElement getFromURL(String spec, String name) throws IOException {
    URL url = new URL(spec);
    URLConnection conn = url.openConnection();
    logger.info(Optional.ofNullable(conn.getContentType())
                    .map(s -> name + " URL: Content-Type=" + s)
                    .orElse(name + " URL: has no Content-Type"));
    return JSON.parse(conn.getInputStream());
  }

  /**
   * Converts a set of validation errors into the "Basic" output format.
   *
   * @param result the validation result
   * @param errors the errors
   * @return a JSON tree containing the formatted Basic output.
   */
  private static JsonObject basicOutput(boolean result,
                                        Map<JSONPath, Map<JSONPath, Error<?>>> errors) {
    JsonObject root = new JsonObject();
    root.addProperty("valid", result);
    JsonArray errorArr = new JsonArray();
    root.add("errors", errorArr);
    errors.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          e.getValue().values().stream()
              .filter(err -> !err.isPruned() && !err.result)
              .sorted(Comparator.comparing(err -> err.loc.schema))
              .forEach(err -> {
                JsonObject error = new JsonObject();
                error.addProperty("schemaLocation", err.loc.schema.toString());
                error.addProperty("absSchemaLocation", err.loc.absSchema.toString());
                error.addProperty("instanceLocation", err.loc.instance.toString());

                if (err.value != null) {
                  error.addProperty("error", err.value.toString());
                }
                errorArr.add(error);
              });
        });
    return root;
  }

  /**
   * Converts a set of annotations into some JSON.
   *
   * @param annotations the annotations
   * @return a JSON tree containing the output.
   */
  private static JsonObject annotationOutput(
      Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations) {
    JsonObject root = new JsonObject();
    JsonArray annotationArr = new JsonArray();
    root.add("annotations", annotationArr);
    annotations.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          e.getValue().forEach((name, bySchemaLoc) -> {
            bySchemaLoc.values().stream()
                .sorted(Comparator.comparing(a -> a.loc.schema))
                .forEach(a -> {
                  JsonObject o = new JsonObject();
                  o.addProperty("instanceLocation", a.loc.instance.toString());
                  o.addProperty("schemaLocation", a.loc.schema.toString());
                  o.addProperty("absSchemaLocation", a.loc.absSchema.toString());
                  JsonObject ao = new JsonObject();
                  o.add("annotation", ao);
                  ao.addProperty("name", a.name);
                  if (!a.isValid()) {
                    ao.addProperty("valid", false);
                  }

                  JsonElement ae;
                  if (a.value == null) {
                    ae = JsonNull.INSTANCE;
                  } else if (a.value instanceof Boolean) {
                    ae = new JsonPrimitive((Boolean) a.value);
                  } else if (a.value instanceof String) {
                    ae = new JsonPrimitive((String) a.value);
                  } else if (a.value instanceof Number) {
                    ae = new JsonPrimitive((Number) a.value);
                  } else if (a.value instanceof Collection) {
                    JsonArray arr = new JsonArray();
                    for (Object elem : (Collection<?>) a.value) {
                      arr.add(String.valueOf(elem));
                    }
                    ae = arr;
                  } else {
                    ae = new JsonPrimitive(a.value.toString());
                  }
                  ao.add("value", ae);
                  annotationArr.add(o);
                });
          });
        });
    return root;
  }
}
