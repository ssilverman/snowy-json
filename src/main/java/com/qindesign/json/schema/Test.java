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
 * Created by shawn on 5/7/20 11:07 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.qindesign.json.schema.util.Logging;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Runs the test suite for multiple specifications. This program takes
 * one argument:
 * <ol>
 * <li>The path to the test suite</li>
 * </ol>
 */
public class Test {
  private static final Class<?> CLASS = Test.class;

  /**
   * Disallow instantiation.
   */
  private Test() {
  }

  private static final Level loggingLevel = Level.CONFIG;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  static {
    Logging.init(logger, loggingLevel);
  }

  private static final String TEST_SCHEMA = "test-schema.json";
  private static final Map<Specification, String> testDirs = Stream.of(new Object[][] {
      { Specification.DRAFT_2019_09, "draft2019-09" },
      { Specification.DRAFT_07, "draft7" },
      { Specification.DRAFT_06, "draft6" },
      }).collect(
      Collectors.toUnmodifiableMap(data -> (Specification) data[0], data -> (String) data[1]));

  /**
   * Holds the results of one test.
   */
  private static final class Result {
    int total;
    int passed;
    int totalOptional;  // How many of the tests are optional?
    int passedOptional;  // How many of the passed tests are optional?
    long duration;  // The test duration
    long totalDuration;  // The duration including overhead
  }

  /**
   * Main program entry point.
   *
   * @param args the program arguments
   * @throws IOException if there was some I/O error.
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1 || 2 < args.length) {
      System.out.println("Usage: " + CLASS.getName() + " <suite location> [output file]");
      System.exit(1);
      return;
    }

    Path root = Path.of(args[0]);
    if (!root.toFile().isDirectory()) {
      logger.severe("Not a directory: " + args[0]);
      System.exit(1);
      return;
    }

    File testSchemaFile = root.resolve(TEST_SCHEMA).toFile();

    // Load the test schema
    long time = System.currentTimeMillis();
    JsonElement testSchema;
    try {
      testSchema = JSON.parse(testSchemaFile);
    } catch (JsonParseException ex) {
      logger.log(Level.SEVERE, "Could not parse test schema: " + testSchemaFile, ex);
      System.exit(1);
      return;
    }
    time = System.currentTimeMillis() - time;
    logger.info("Loaded test schema (" + time/1000.0f + "s)");

    Map<URI, JsonElement> knownIDs = Collections.emptyMap();
    Map<URI, URL> knownURLs = Map.of(URI.parseUnchecked("http://localhost:1234"),
                                     root.resolve("remotes").toUri().toURL());

    // Potentially gather all the output
    JsonArray specOutputArr = null;
    if (args.length >= 2) {
      specOutputArr = new JsonArray();
    }

    time = System.currentTimeMillis();
    for (Specification spec : Specification.values()) {
      if (!testDirs.containsKey(spec)) {
        continue;
      }

      System.out.println();

      Path testDir = root.resolve("tests/" + testDirs.get(spec));
      if (!testDir.toFile().isDirectory()) {
        logger.severe("Not a directory: " + testDir);
        continue;
      }

      logger.info("Running tests: " + spec);
      Map<String, Result> results = new TreeMap<>();
      JsonArray testOutputArr = null;
      if (specOutputArr != null) {
        JsonObject specObj = new JsonObject();
        specOutputArr.add(specObj);
        specObj.addProperty("spec", spec.toString());
        testOutputArr = new JsonArray();
        specObj.add("tests", testOutputArr);
      }
      Result specResult = runSpec(root, testDir, testSchema, testSchemaFile, results, spec,
                                  knownIDs, knownURLs, testOutputArr);
      printSpecResults(spec, specResult, results);
    }
    time = System.currentTimeMillis() - time;
    System.out.println();
    logger.info("Total test time: " + time/1000.0f + "s");

    // Print the output
    if (specOutputArr != null) {
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]))) {
        JSON.print(bw, specOutputArr, "    ");
        bw.newLine();
        bw.flush();
      } catch (IOException ex) {
        logger.log(Level.SEVERE, "Error writing output", ex);
      }
    }
  }

  /**
   * Prints the results of one test run for a specification.
   *
   * @param spec the specification
   * @param specResult the total result
   * @param results the suite results
   */
  private static void printSpecResults(Specification spec,
                                       Result specResult, Map<String, Result> results) {
    System.out.println("Results for specification: " + spec);

    int maxLen = results.keySet().stream().mapToInt(String::length).max().orElse(1);
    System.out.printf("%-" + maxLen + "s  %-4s  %-4s  %-5s  %-8s\n", "Name", "Pass", "Fail", "Total", "Duration");
    IntStream.range(0, maxLen + 29).forEach(i -> System.out.print('-'));
    System.out.println();
    results.forEach((name, result) -> {
      System.out.printf("%-" + maxLen + "s  %4d  %4d  %5d  %7ss\n",
                        name, result.passed, result.total - result.passed, result.total,
                        formatDuration(result.duration));
    });
    IntStream.range(0, maxLen + 29).forEach(i -> System.out.print('-'));
    System.out.println();
    System.out.printf("Pass:%d (%d opt) Fail:%d (%d opt) Total:%d (%d opt) Time:%ss\n",
                      specResult.passed, specResult.passedOptional,
                      specResult.total - specResult.passed,
                      specResult.totalOptional - specResult.passedOptional,
                      specResult.total,
                      specResult.totalOptional,
                      formatDuration(specResult.duration).trim());
    System.out.println("Times:");
    System.out.printf("  Test: %ss\n", formatDuration(specResult.duration).trim());
    System.out.printf("  Other: %ss\n", formatDuration(specResult.totalDuration - specResult.duration).trim());
    System.out.printf("  Total: %ss\n", formatDuration(specResult.totalDuration).trim());
}

  /**
   * Formats a duration in milliseconds.
   *
   * @param millis the duration to format
   * @return the formatted duration.
   */
  private static String formatDuration(long millis) {
    return String.format("%2d.%03d", millis/1000, millis%1000);
  }

  /**
   * Runs all the tests for a specification.
   *
   * @param dir the test directory
   * @param testSchema the test schema elements, for validating the suites
   * @param testSchemaFile the test schema file
   * @param results all the test results for this specification will be put here
   * @param spec the specification
   * @param knownIDs any known JSON contents
   * @param knownURLs any known resources
   * @param testOutputArr the test results, all the annotations and errors, may
   *                      be {@code null}
   * @return the test results.
   */
  private static Result runSpec(Path root, Path dir, JsonElement testSchema, File testSchemaFile,
                                Map<String, Result> results, Specification spec,
                                Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                                JsonArray testOutputArr)
      throws IOException {
    Result result = new Result();
    long start = System.currentTimeMillis();

    // Validate and test as we go
    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        JsonElement instance;
        try {
          instance = JSON.parse(file.toFile());
        } catch (JsonParseException ex) {
          logger.log(Level.WARNING, "Could not parse test suite: " + file, ex);
          return FileVisitResult.CONTINUE;
        }

        // Validate the test
        logger.fine("Validating test suite: " + file);
        Options opts = new Options();
        opts.set(Option.FORMAT, false);
        opts.set(Option.CONTENT, false);
        opts.set(Option.DEFAULT_SPECIFICATION, spec);
        try {
          Validator validator =
              new Validator(testSchema, new URI(testSchemaFile.toURI()), knownIDs, knownURLs, opts);
          if (!validator.validate(instance, new HashMap<>(), null)) {
            logger.warning("Not a valid test suite: " + file);
            return FileVisitResult.CONTINUE;
          }
        } catch (MalformedSchemaException ex) {
          logger.log(Level.SEVERE, "Malformed schema: " + testSchemaFile, ex);
          return FileVisitResult.CONTINUE;
        }

        // Run the suite
        Result r = runSuite(root, file, instance.getAsJsonArray(), spec, knownIDs, knownURLs,
                            testOutputArr);
        result.total += r.total;
        result.passed += r.passed;
        result.totalOptional += r.totalOptional;
        result.passedOptional += r.passedOptional;
        result.duration += r.duration;
        results.put(dir.relativize(file).toString(), r);

        return FileVisitResult.CONTINUE;
      }
    });

    result.totalDuration = System.currentTimeMillis() - start;
    return result;
  }

  /**
   * Runs a set of tests.
   *
   * @param root the test root, for better error printing
   * @param file the suite file
   * @param suite the suite JSON tree
   * @param spec the specification
   * @param knownIDs any known JSON contents
   * @param knownURLs any known resources
   * @param testOutputArr the test results, all the annotations and errors, may
   *                      be {@code null}
   * @return the suite results.
   */
  private static Result runSuite(Path root, Path file, JsonArray suite, Specification spec,
                                 Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                                 JsonArray testOutputArr) {
    Result suiteResult = new Result();
    long start = System.currentTimeMillis();

    int groupIndex = 0;
    for (JsonElement g : suite) {
      JsonObject group = g.getAsJsonObject();
      String groupDescription = group.get("description").getAsString();
      JsonElement schema = group.get("schema");

      int testIndex = 0;
      for (JsonElement t : group.getAsJsonArray("tests")) {
        JsonObject test = t.getAsJsonObject();
        String description = test.get("description").getAsString();
        JsonElement data = test.get("data");
        boolean valid = test.get("valid").getAsBoolean();

        URI uri = new URI(file.toUri());
        try {
          uri = new URI(uri.scheme(), uri.authority(),
                        uri.path() + "/" + groupIndex + "/" + testIndex,
                        uri.query(), null);
        } catch (URISyntaxException ex) {
          throw new IllegalArgumentException(ex);
        }

        boolean isOptional = uri.rawPath().contains("/optional/");

        suiteResult.total++;
        if (isOptional) {
          suiteResult.totalOptional++;
        }
        logger.fine("Testing " + uri);
        Options opts = new Options();
        opts.set(Option.FORMAT, true);
        opts.set(Option.CONTENT, true);
        opts.set(Option.DEFAULT_SPECIFICATION, spec);
        try {
          Validator validator = new Validator(schema, uri, knownIDs, knownURLs, opts);
          Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations = new HashMap<>();
          Map<JSONPath, Map<JSONPath, Error<?>>> errors = null;
          if (testOutputArr != null) {
            errors = new HashMap<>();
          }
          boolean result = validator.validate(data, annotations, errors);
          if (result != valid) {
            logger.info(new URI(root.toUri()).relativize(uri) + ": Bad result: " +
                        groupDescription + ": " + description +
                        ": got=" + result + " want=" + valid);
          } else {
            suiteResult.passed++;
            if (isOptional) {
              suiteResult.passedOptional++;
            }
          }

          // Add output for this test
          if (testOutputArr != null) {
            JsonObject testObj = new JsonObject();
            testOutputArr.add(testObj);
            testObj.addProperty("test", uri.toString());
            testObj.addProperty("passed", result == valid);
            testObj.addProperty("valid", result);
            JsonArray errorArr = new JsonArray();
            testObj.add("errors", errorArr);
            JsonArray annotationArr = new JsonArray();
            testObj.add("annotations", annotationArr);

            errors.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                  e.getValue().values().stream()
                      .sorted(Comparator.comparing(err -> err.loc.keyword))
                      .forEach(err -> {
                        JsonObject error = new JsonObject();
                        error.addProperty("keywordLocation", err.loc.keyword.toString());
                        error.addProperty("absoluteKeywordLocation", err.loc.absKeyword.toString());
                        error.addProperty("instanceLocation", err.loc.instance.toString());
                        if (err.isPruned()) {
                          error.addProperty("pruned", true);
                        }

                        error.addProperty("result", err.result);
                        if (err.value != null) {
                          error.addProperty("error", err.value.toString());
                        }
                        errorArr.add(error);
                      });
                });

            annotations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                  e.getValue().forEach((name, bySchemaLoc) -> {
                    bySchemaLoc.values().stream()
                        .sorted(Comparator.comparing(a -> a.loc.keyword))
                        .forEach(a -> {
                          JsonObject o = new JsonObject();
                          o.addProperty("instanceLocation", a.loc.instance.toString());
                          o.addProperty("keywordLocation", a.loc.keyword.toString());
                          o.addProperty("absoluteKeywordLocation", a.loc.absKeyword.toString());
                          JsonObject ao = new JsonObject();
                          o.add("annotation", ao);
                          ao.addProperty("name", a.name);
                          if (!a.isValid()) {
                            ao.addProperty("pruned", true);
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
          }
        } catch (MalformedSchemaException ex) {
          if (valid) {
            logger.info(new URI(root.toUri()).relativize(uri) + ": Bad result: " +
                        groupDescription + ": " + description +
                        ": got=Malformed schema: " + ex.getMessage());
          } else {
            suiteResult.passed++;
            if (isOptional) {
              suiteResult.passedOptional++;
            }
          }
        } catch (IllegalArgumentException ex) {
          logger.info(new URI(root.toUri()).relativize(uri) + ": Bad result: " +
                      groupDescription + ": " + description +
                      ": got=Bad argument: " + ex.getMessage());
        }
        testIndex++;
      }

      groupIndex++;
    }

    suiteResult.duration = System.currentTimeMillis() - start;
    suiteResult.totalDuration = suiteResult.duration;
    return suiteResult;
  }
}
