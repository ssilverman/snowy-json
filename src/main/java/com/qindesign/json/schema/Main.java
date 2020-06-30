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
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.qindesign.json.schema.util.Logging;
import com.qindesign.net.URI;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Collections;
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

    Map<JSONPath, Map<JSONPath, Annotation>> errors = new HashMap<>();
    Map<JSONPath, Map<String, Map<JSONPath, Annotation>>> annotations = new HashMap<>();

    long time = System.currentTimeMillis();
    boolean result = Validator.validate(schema, instance, schemaID,
                                        Collections.emptyMap(), Collections.emptyMap(),
                                        opts, annotations, errors);
    time = System.currentTimeMillis() - time;
    logger.info("Validation result: " + result + " (" + time/1000.0 + "s)");

    // Basic output
    Writer out = new OutputStreamWriter(System.out);
    JsonWriter w = new JsonWriter(out);
    w.setIndent("    ");
    System.out.println("Basic output:");
    Streams.write(basicOutput(result, errors), w);
    w.flush();
    System.out.println();

    // Annotations
    System.out.println();
    w = new JsonWriter(out);
    w.setIndent("    ");
    System.out.println("Annotations:");
    Streams.write(annotationOutput(annotations), w);
    w.flush();
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
                                        Map<JSONPath, Map<JSONPath, Annotation>> errors) {
    JsonObject root = new JsonObject();
    root.add("valid", new JsonPrimitive(result));
    JsonArray errorArr = new JsonArray();
    root.add("errors", errorArr);
    errors.forEach((instancePath, map) -> {
      map.forEach((schemaPath, a) -> {
        JsonObject error = new JsonObject();
        error.addProperty("keywordLocation", a.keywordLocation.toString());
        error.addProperty("absoluteKeywordLocation", a.absKeywordLocation.toString());
        error.addProperty("instanceLocation", a.instanceLocation.toString());

        ValidationResult vr = (ValidationResult) a.value;
        if (vr.result) {
          return;
        }
        if (vr.message == null) {
          error.add(a.name, JsonNull.INSTANCE);
        } else {
          error.add(a.name, new JsonPrimitive(vr.message));
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
      Map<JSONPath, Map<String, Map<JSONPath, Annotation>>> annotations) {
    JsonObject root = new JsonObject();
    JsonArray annotationArr = new JsonArray();
    root.add("annotations", annotationArr);
    annotations.forEach((instanceLoc, byName) -> {
      byName.forEach((name, bySchemaLoc) -> {
        bySchemaLoc.forEach((schemaLoc, a) -> {
          JsonObject o = new JsonObject();
          o.addProperty("instanceLocation", a.instanceLocation.toString());
          o.addProperty("keywordLocation", a.keywordLocation.toString());
          o.addProperty("absoluteKeywordLocation", a.absKeywordLocation.toString());
          JsonObject ao = new JsonObject();
          o.add("annotation", ao);
          ao.add("name", new JsonPrimitive(a.name));
          if (!a.isValid()) {
            ao.add("valid", new JsonPrimitive(false));
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
            for (Object e : (Collection<?>) a.value) {
              arr.add(String.valueOf(e));
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
