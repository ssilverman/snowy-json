/*
 * Created by shawn on 5/7/20 11:07 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Runs the test suite for multiple specifications.
 */
public class Test {
  private static final Class<?> CLASS = Main.class;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  private static final String TEST_SCHEMA = "test-schema.json";
  private static final Map<Specification, String> testDirs = Stream.of(new Object[][] {
      { Specification.DRAFT_2019_09, "draft2019-09" },
      { Specification.DRAFT_07, "draft7" },
      { Specification.DRAFT_06, "draft6" },
  }).collect(Collectors.toMap(data -> (Specification) data[0], data -> (String) data[1]));

  /**
   * Holds the results of one test.
   */
  private static final class Result {
    int total;
    int passed;
    long duration;  // The test duration
    long totalDuration;  // The duration including overhead
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: " + CLASS.getName() + " <suite location>");
      System.exit(1);
      return;
    }

    Path root = Path.of(args[0]);
    if (!root.toFile().isDirectory()) {
      logger.severe("Not a directory: " + args[0]);
      System.exit(1);
    }

    File testSchemaFile = root.resolve(TEST_SCHEMA).toFile();

    // Load the test schema
    JsonElement testSchema;
    try {
      testSchema = JSON.parse(testSchemaFile);
    } catch (JsonParseException ex) {
      logger.log(Level.SEVERE, "Could not parse test schema: " + testSchemaFile, ex);
      System.exit(1);
      return;
    }
    logger.info("Loaded test schema");

    Map<URI, JsonElement> knownIDs = Collections.emptyMap();
    Map<URI, URL> knownURLs = Map.of(URI.create("http://localhost:1234"),
                                     root.resolve("remotes").toUri().toURL());

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
      Result specResult = runSpec(root, testDir, testSchema, testSchemaFile, results, spec,
                                  knownIDs, knownURLs);
      printSpecResults(spec, specResult, results);
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
    System.out.printf("%-" + maxLen + "s  %-4s  %-4s  %-5s  %-9s\n", "Name", "Pass", "Fail", "Total", "Duration");
    IntStream.range(0, maxLen + 30).forEach(i -> System.out.print('-'));
    System.out.println();
    results.forEach((name, result) -> {
      System.out.printf("%-" + maxLen + "s  %4d  %4d  %5d  %8ss\n",
                        name, result.passed, result.total - result.passed, result.total,
                        formatDuration(result.duration));
    });
    IntStream.range(0, maxLen + 30).forEach(i -> System.out.print('-'));
    System.out.println();
    System.out.printf("Pass:%d Fail:%d Total:%d Time:%ss\n",
                      specResult.passed, specResult.total - specResult.passed, specResult.total,
                      formatDuration(specResult.duration).trim());
    System.out.println("Times:");
    System.out.printf("  Test: %ss\n", formatDuration(specResult.duration));
    System.out.printf("  Other: %ss\n", formatDuration(specResult.totalDuration - specResult.duration));
    System.out.printf("  Total: %ss\n", formatDuration(specResult.totalDuration));
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
   * @return the test results.
   */
  private static Result runSpec(Path root, Path dir, JsonElement testSchema, File testSchemaFile,
                                Map<String, Result> results, Specification spec,
                                Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs)
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
        opts.set(Option.COLLECT_ANNOTATIONS, false);
        opts.set(Option.COLLECT_ERRORS, false);
        try {
          if (!Validator.validate(testSchema, instance, testSchemaFile.toURI(), spec,
                                  knownIDs, knownURLs, opts)) {
            logger.warning("Not a valid test suite: " + file);
            return FileVisitResult.CONTINUE;
          }
        } catch (MalformedSchemaException ex) {
          logger.log(Level.SEVERE, "Malformed schema: " + testSchemaFile, ex);
          return FileVisitResult.CONTINUE;
        }

        // Run the suite
        Result r = runSuite(root, file, instance.getAsJsonArray(), spec, knownIDs, knownURLs);
        result.total += r.total;
        result.passed += r.passed;
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
   * @return the suite results.
   */
  private static Result runSuite(Path root, Path file, JsonArray suite, Specification spec,
                                 Map<URI, JsonElement> knownIDs, Map<URI, URL> knownURLs) {
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

        URI uri = file.toUri();
        try {
          uri = new URI(uri.getScheme(),
                        uri.getRawSchemeSpecificPart() + "/" + groupIndex + "/" + testIndex,
                        null);
        } catch (URISyntaxException ex) {
          throw new IllegalArgumentException(ex);
        }

        suiteResult.total++;
        logger.fine("Testing " + uri);
        Options opts = new Options();
        opts.set(Option.FORMAT, true);
        opts.set(Option.CONTENT, true);
        opts.set(Option.COLLECT_ERRORS, false);  // Don't collect errors because
                                                 // we may be validating against
                                                 // $recursiveRefs
        try {
          boolean result = Validator.validate(schema, data, uri, spec, knownIDs, knownURLs, opts);
          if (result != valid) {
            logger.info(root.toUri().relativize(uri) + ": Bad result: " +
                        groupDescription + ": " + description +
                        ": got=" + result + " want=" + valid);
          } else {
            suiteResult.passed++;
          }
        } catch (MalformedSchemaException ex) {
          if (valid) {
            logger.info(root.toUri().relativize(uri) + ": Bad result: " +
                        groupDescription + ": " + description +
                        ": got=Malformed schema: " + ex.getMessage());
          } else {
            suiteResult.passed++;
          }
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
