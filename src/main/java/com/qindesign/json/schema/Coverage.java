/*
 * Created by shawn on 6/25/20 9:30 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.keywords.CoreDefs;
import com.qindesign.json.schema.keywords.Definitions;
import com.qindesign.json.schema.keywords.Properties;
import com.qindesign.json.schema.util.Logging;
import com.qindesign.net.URI;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * A rudimentary schema coverage checker. This is an example program.
 * <p>
 * This program takes two arguments:
 * <ol>
 * <li>Schema path or URL</li>
 * <li>Instance path or URL</li>
 * </ol>
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
    opts.set(Option.COLLECT_ANNOTATIONS, false);

    Map<JSONPath, Map<JSONPath, Annotation>> errors = new HashMap<>();

    long time = System.currentTimeMillis();
    boolean result = Validator.validate(schema, instance, schemaID,
                                        Collections.emptyMap(), Collections.emptyMap(),
                                        opts, null, errors);
    time = System.currentTimeMillis() - time;
    logger.info("Validation result: " + result + " (" + time/1000.0 + "s)");

    // Coverage collection using the errors
    Set<JSONPath> all = mapSchema(schema, (Specification) opts.get(Option.DEFAULT_SPECIFICATION));
    Set<JSONPath> seen = new HashSet<>();
    errors.forEach((schemaPath, byInstance) -> {
      byInstance.values()
          .forEach(a -> {
            String fragment = Optional.ofNullable(a.absKeywordLocation.fragment()).orElse("");
            seen.add(JSONPath.fromJSONPointer(fragment));
          });
    });

    int seenCount = seen.size();
    int totalCount = all.size();
    boolean seenHasRoot = false;
    boolean allHasRoot = false;
    if (seen.remove(JSONPath.absolute())) {
      seenCount--;
      seenHasRoot = true;
    }
    if (all.remove(JSONPath.absolute())) {
      totalCount--;
      allHasRoot = true;
    }

    // More complex analysis could be done here
    // Note that we're removing the empty paths when printing
    System.out.println();
    System.out.println("Seen " + seenCount + " (excluding root):");
    IntStream.range(0, Integer.toString(seenCount).length() + 23)
        .forEach(i -> System.out.print('-'));
    System.out.println();
    if (!seen.isEmpty()) {
      seen.stream().sorted().forEach(System.out::println);
    } else {
      System.out.println("<None>");
    }

    all.removeAll(seen);
    System.out.println();
    System.out.println("Not seen " + all.size() + " (excluding root):");
    IntStream.range(0, Integer.toString(all.size()).length() + 27)
        .forEach(i -> System.out.print('-'));
    System.out.println();
    if (!all.isEmpty()) {
      all.stream().sorted().forEach(System.out::println);
    } else {
      System.out.println("<None>");
    }

    System.out.println();
    if (seenHasRoot) {
      seenCount++;
    }
    if (allHasRoot) {
      totalCount++;
    }
    float percent = (float) seenCount / (float) totalCount * 100.0f;
    System.out.println("Total (including root): Seen " + seenCount + "/" + totalCount + " (" + percent + "%)");
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
    JSON.traverse(schema, (e, parent, path, state) -> {
      // Determine the specification
      Specification spec = state.spec();
      if (spec == null) {
        spec = defaultSpec;
      }

      // Checks if the path has a parent with the given name
      BiFunction<JSONPath, String, Boolean> isIn =
          (p, name) -> p.size() >= 2 && p.get(p.size() - 2).equals(name);

      boolean inDefs;  // Note: This is only a rudimentary check
      if (spec != null) {
        if (spec.ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          inDefs = isIn.apply(path, CoreDefs.NAME);
        } else {
          inDefs = isIn.apply(path, Definitions.NAME);
        }
      } else {
        inDefs = isIn.apply(path, CoreDefs.NAME) ||
                 isIn.apply(path, Definitions.NAME);
      }

      // No definitions parent
      if (inDefs) {
        return;
      }

      // No "properties" parent
      if (isIn.apply(path, Properties.NAME)) {
        return;
      }

      // No array parent
      if (parent != null && parent.isJsonArray()) {
        return;
      }

      paths.add(path);
    });
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
