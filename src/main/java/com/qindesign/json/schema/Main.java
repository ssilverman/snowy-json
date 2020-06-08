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
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    // Uncomment to do auto-resolution
//    opts.set(Option.AUTO_RESOLVE, true);

    Map<String, Map<String, Annotation>> errors = new HashMap<>();

    boolean result = Validator.validate(schema, instance, schemaID,
                                        Collections.emptyMap(), Collections.emptyMap(),
                                        opts, null, errors);
    logger.info("Validation result: " + result);

    JsonWriter w = new JsonWriter(new OutputStreamWriter(System.out));
    w.setIndent("    ");
    Streams.write(basicOutput(result, errors), w);
    w.flush();
    System.out.println();
  }

  /**
   * Converts a set of validation errors into the "Basic" output format.
   *
   * @param result the validation result
   * @param errors the errors
   * @return a JSON tree containing the formatted Basic output.
   */
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
