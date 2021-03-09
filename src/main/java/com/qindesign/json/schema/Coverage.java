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
 * Created by shawn on 6/25/20 9:30 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.util.Logging;
import com.qindesign.json.schema.net.URI;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * <p>
 * There are two output JSON objects: one by instance location and another
 * by seen schema locations.
 */
public class Coverage {
  private static final Class<?> CLASS = Coverage.class;

  /**
   * Disallow instantiation.
   */
  private Coverage() {
  }

  private static final Level loggingLevel = Level.CONFIG;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  static {
    Logging.init(logger, loggingLevel);
  }

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

    Map<JSONPath, Map<JSONPath, Error<?>>> errors = new HashMap<>();

    long time = System.currentTimeMillis();
    Validator validator = new Validator(schema, schemaID, null, null, opts);
    boolean result = validator.validate(instance, null, errors);
    time = System.currentTimeMillis() - time;
    logger.info("Validation result: " + result + " (" + time/1000.0 + "s)");

    // Coverage collection uses the errors
    // More complex analysis could be done here

    JsonObject instanceRoot = new JsonObject();
    JsonArray seenInstanceLocs = new JsonArray();
    instanceRoot.add("seen", seenInstanceLocs);

    // All seen instance locations
    errors.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> {
          JsonObject instanceLoc = new JsonObject();
          instanceLoc.addProperty("instanceLocation", entry.getKey().toString());
          JsonArray schemaLocs = new JsonArray();
          instanceLoc.add("schemaLocations", schemaLocs);
          entry.getValue().values().stream()
              .sorted(Comparator.comparing(err -> err.loc.schema))
              .forEach(err -> {
                JsonObject schemaLoc = new JsonObject();
                schemaLoc.addProperty("schemaLocation", err.loc.schema.toString());
                schemaLoc.addProperty("absSchemaLocation", err.loc.absSchema.toString());
                schemaLocs.add(schemaLoc);
              });
          seenInstanceLocs.add(instanceLoc);
        });

    // Unseen instance locations
    JsonArray unseenInstanceLocs = new JsonArray();
    instanceRoot.add("unseen", unseenInstanceLocs);
    JSON.traverse(instance, (e, parent, path) -> {
      if (!errors.containsKey(path)) {
        unseenInstanceLocs.add(path.toString());
      }
    });

    // Gather just the seen schema locations
    // It's a more difficult problem to collect unseen, so leave that out in
    // this example program
    JsonObject schemaRoot = new JsonObject();
    JsonArray seenSchemaLocs = new JsonArray();
    schemaRoot.add("seen", seenSchemaLocs);
    errors.forEach((instanceLoc, bySchema) -> {
      bySchema.values()
          .forEach(err -> {
            JsonObject schemaLoc = new JsonObject();
            schemaLoc.addProperty("schemaLocation", err.loc.schema.toString());
            schemaLoc.addProperty("absSchemaLocation", err.loc.absSchema.toString());
            seenSchemaLocs.add(schemaLoc);
          });
    });

    // Output
    System.out.println("By instance location:");
    JSON.print(System.out, instanceRoot, "    ");
    System.out.println();

    System.out.println();
    System.out.println("By schema location (seen only):");
    JSON.print(System.out, schemaRoot, "    ");
    System.out.println();
  }

  /**
   * Maps out a schema and returns a set containing all the paths.
   * <p>
   * This excludes paths for certain cases:
   * <ol>
   * <li>Elements having a "properties" parent</li>
   * <li>Array elements</li>
   * <li>Draft 2019-09 and later:
   *     <ol>
   *     <li>Elements having a "$defs" parent.</li>
   *     </ol></li>
   * <li>Drafts prior to Draft 2019-09:
   *     <ol>
   *     <li>Elements having a "definitions" parent.</li>
   *     </ol></li>
   * <li>Unknown specification:
   *     <ol>
   *     <li>Elements having either a "$defs" or a "definitions" parent.</li>
   *     </ol></li>
   * </ol>
   *
   * @param schema the schema
   * @param defaultSpec the default specification to use to examine elements,
   *                    may be {@code null}
   * @return a set containing all the paths in the schema.
   */
  private static Set<JSONPath> mapSchema(JsonElement schema, Specification defaultSpec) {
    Set<JSONPath> paths = new HashSet<>();
    try {
      // TODO: What base URI to use?
      JSON.traverseSchema(URI.parseUnchecked(""), defaultSpec, schema, (e, parent, path, state) -> {
        if (state.isNotKeyword()) {
          return;
        }

        // No array parent
        if (parent != null && parent.isJsonArray()) {
          return;
        }

        paths.add(path);
      });
    } catch (MalformedSchemaException ex) {
      throw new RuntimeException(ex);
    }
    return paths;
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
}
