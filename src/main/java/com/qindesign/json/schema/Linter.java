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
 * Created by shawn on 5/16/20 9:15 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.keywords.*;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A rudimentary linter.
 */
public final class Linter {
  private static final Class<?> CLASS = Linter.class;

  /**
   * Disallow instantiation.
   */
  private Linter() {
  }

  /** The set of known formats. */
  private static final Set<String> KNOWN_FORMATS = Set.of(
      "date-time",
      "date",
      "time",
      "duration",
      "full-date",
      "full-time",
      "email",
      "idn-email",
      "hostname",
      "idn-hostname",
      "ipv4",
      "ipv6",
      "uri",
      "uri-reference",
      "iri",
      "iri-reference",
      "uuid",
      "uri-template",
      "json-pointer",
      "relative-json-pointer",
      "regex");

  /** The set of known keywords. */
  private static final Set<String> KNOWN_KEYWORDS = Set.of(
      "$anchor", "$comment", "$defs", "$id",
      "$recursiveAnchor", "$recursiveRef", "$ref", "$schema",
      "$vocabulary", "additionalItems", "additionalProperties", "allOf",
      "anyOf", "const", "contains", "contentEncoding",
      "contentMediaType", "contentSchema", "default", "definitions",
      "dependencies", "dependentRequired", "dependentSchemas", "deprecated",
      "description", "enum", "examples", "exclusiveMinimum",
      "exclusiveMaximum", "format", "if", "then",
      "else", "items", "maxContains", "maximum",
      "maxItems", "maxLength", "maxProperties", "minContains",
      "minimum", "minItems", "minLength", "minProperties",
      "multipleOf", "not", "oneOf", "pattern",
      "patternProperties", "properties", "propertyNames", "readOnly",
      "required", "title", "type", "unevaluatedItems",
      "unevaluatedProperties", "uniqueItems", "writeOnly");

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: " + CLASS.getName() + " <schema>");
      System.exit(1);
      return;
    }

    JsonElement schema;
    try {
      URL url = new URL(args[0]);
      URLConnection conn = url.openConnection();
      System.out.println(Optional.ofNullable(conn.getContentType())
                             .map(s -> "Schema URL: Content-Type=" + s)
                             .orElse("Schema URL: has no Content-Type"));
      schema = JSON.parse(conn.getInputStream());
    } catch (MalformedURLException ex) {
      schema = JSON.parse(new File(args[0]));
    }
    var issues = check(schema);
    issues.forEach((path, list) -> list.forEach(msg -> System.out.println(path + ": " + msg)));
  }

  /**
   * Convenience method that adds an issue to the issue map.
   *
   * @param issues the map of issues
   * @param path the path
   * @param msg the message
   */
  private static void addIssue(Map<List<String>, List<String>> issues,
                               List<String> path,
                               String msg) {
    issues.computeIfAbsent(path, k -> new ArrayList<>()).add(msg);
  }

  /**
   * Checks if the path has a parent with the name {@code parentName}.
   *
   * @param path the path
   * @param parentName the parent name to check
   * @return whether the parent is {@code parentName}.
   */
  private static boolean isInParent(List<String> path, String parentName) {
    if (path.size() < 2) {
      return false;
    }
    return path.get(path.size() - 2).equals(parentName);
  }

  /**
   * Checks if the path ends with the given name.
   *
   * @param path the path
   * @param name the name to check
   * @return whether the last element is {@code name}.
   */
  private static boolean is(List<String> path, String name) {
    if (path.isEmpty()) {
      return false;
    }
    return path.get(path.size() - 1).equals(name);
  }

  /**
   * Checks the given schema and returns lists of any issues found for each
   * element in the tree. This returns a map of JSON element locations to a
   * list of associated issue messages.
   * <p>
   * Each location will be a list of strings, where each element in the location
   * is the name of a property. To convert each path element to JSON Pointer
   * form, see {@link Strings#jsonPointerToken(String)}.
   *
   * @param schema the schema to check
   * @return the linter results, mapping locations to a list of issues.
   * @see Strings#jsonPointerToken(String)
   */
  public static Map<List<String>, List<String>> check(JsonElement schema) {
    Map<List<String>, List<String>> issues = new HashMap<>();

    JSON.traverse(schema, (e, parent, path, state) -> {
      if (e.isJsonNull()) {
        return;
      }

      // Convenience function for adding a maybe-null issue
      Consumer<String> addIssue = (String msg) -> {
        if (msg != null) {
          addIssue(issues, path, msg);
        }
      };

      if (e.isJsonPrimitive()) {
        if (isInParent(path, Properties.NAME)) {
          return;
        }
        if (path.isEmpty()) {
          return;
        }

        switch (path.get(path.size() - 1)) {
          case Format.NAME:
            if (JSON.isString(e)) {
              if (!KNOWN_FORMATS.contains(e.getAsString())) {
                addIssue.accept("unknown format: \"" + Strings.jsonString(e.getAsString()) + "\"");
              }
            }
            break;

          case CoreId.NAME:
            if (JSON.isString(e)) {
              try {
                URI id = URI.parse(e.getAsString());
                if (!id.normalize().equals(id)) {
                  addIssue.accept("unnormalized ID: \"" + Strings.jsonString(e.getAsString()) + "\"");
                }
                if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
                  if (id.rawFragment() != null && id.rawFragment().isEmpty()) {
                    addIssue.accept("empty fragment: \"" + Strings.jsonString(e.getAsString()) + "\"");
                  }
                }
              } catch (URISyntaxException ex) {
                // Ignore
              }
            }
            break;

          case CoreRef.NAME:
            // Only examine $refs that are just fragments
            if (JSON.isString(e)) {
              String ref = e.getAsString();
              if (ref.startsWith("#")) {
                boolean first = true;
                JsonElement o = schema;
                for (String part : ref.substring(1).split("/", -1)) {
                  if (first) {
                    if (!part.isEmpty()) {
                      addIssue.accept("bad JSON Pointer: \"" + ref + "\"");
                      break;
                    }
                    first = false;
                    continue;
                  }
                  if (!o.isJsonObject() || !o.getAsJsonObject().has(part)) {
                    addIssue.accept("reference not found: \"" + Strings.jsonString(ref) + "\"");
                    break;
                  }
                  o = o.getAsJsonObject().get(part);
                }
              }
            }
            break;
        }

        return;
      }

      if (e.isJsonArray()) {
        JsonArray array = e.getAsJsonArray();

        if (is(path, Items.NAME)) {
          if (array.size() <= 0) {
            addIssue.accept("empty items array");
          }
        }

        return;
      }

      JsonObject object = e.getAsJsonObject();

      // If we're in a properties object then don't look at the names
      if (is(path, Properties.NAME)) {
        object.keySet().forEach(name -> {
          if (name.startsWith("$")) {
            addIssue.accept("property name starts with '$': \"" + Strings.jsonString(name) + "\"");
          }
        });

        return;
      }

      boolean inDefs;  // Note: This is only a rudimentary check
      if (state.spec() != null) {
        if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          inDefs = is(path, CoreDefs.NAME);
        } else {
          inDefs = is(path, Definitions.NAME);
        }
      } else {
        inDefs = is(path, CoreDefs.NAME) ||
                 is(path, Definitions.NAME);
      }

      // Allow anything directly below defs
      if (inDefs) {
        return;
      }

      if (object.has(CoreSchema.NAME)) {
        if (parent != null && !object.has(CoreId.NAME)) {
          addIssue.accept("\"" + CoreSchema.NAME +
                          "\" in subschema without sibling \"" +
                          CoreId.NAME + "\"");
        }
      }

      if (object.has(AdditionalItems.NAME)) {
        JsonElement items = object.get(Items.NAME);
        if (items == null || !items.isJsonArray()) {
          addIssue.accept("\"" + AdditionalItems.NAME + "\" without array-form \"items\"");
        }
      }

      // Minimum > maximum checks for all drafts
      addIssue.accept(compareExclusiveMinMax(object, ExclusiveMinimum.NAME, ExclusiveMaximum.NAME));
      addIssue.accept(compareMinMax(object, Minimum.NAME, Maximum.NAME));
      addIssue.accept(compareMinMax(object, MinItems.NAME, MaxItems.NAME));
      addIssue.accept(compareMinMax(object, MinLength.NAME, MaxLength.NAME));
      addIssue.accept(compareMinMax(object, MinProperties.NAME, MaxProperties.NAME));

      // Type checks for all drafts
      addIssue.accept(checkType(object, AdditionalItems.NAME, List.of("array")));
      addIssue.accept(checkType(object, AdditionalProperties.NAME, List.of("object")));
      addIssue.accept(checkType(object, Contains.NAME, List.of("array")));
      addIssue.accept(checkType(object, ExclusiveMaximum.NAME, List.of("number", "integer")));
      addIssue.accept(checkType(object, ExclusiveMinimum.NAME, List.of("number", "integer")));
      addIssue.accept(checkType(object, Format.NAME, List.of("string")));
      addIssue.accept(checkType(object, Items.NAME, List.of("array")));
      addIssue.accept(checkType(object, Maximum.NAME, List.of("number", "integer")));
      addIssue.accept(checkType(object, MaxItems.NAME, List.of("array")));
      addIssue.accept(checkType(object, MaxLength.NAME, List.of("string")));
      addIssue.accept(checkType(object, MaxProperties.NAME, List.of("object")));
      addIssue.accept(checkType(object, Minimum.NAME, List.of("number", "integer")));
      addIssue.accept(checkType(object, MinItems.NAME, List.of("array")));
      addIssue.accept(checkType(object, MinLength.NAME, List.of("string")));
      addIssue.accept(checkType(object, MinProperties.NAME, List.of("object")));
      addIssue.accept(checkType(object, MultipleOf.NAME, List.of("number", "integer")));
      addIssue.accept(checkType(object, Pattern.NAME, List.of("string")));
      addIssue.accept(checkType(object, PatternProperties.NAME, List.of("object")));
      addIssue.accept(checkType(object, Properties.NAME, List.of("object")));
      addIssue.accept(checkType(object, PropertyNames.NAME, List.of("object")));
      addIssue.accept(checkType(object, Required.NAME, List.of("object")));
      addIssue.accept(checkType(object, UniqueItems.NAME, List.of("array")));

      // Check specification-specific keyword presence
      if (state.spec() != null) {
        if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          object.keySet().forEach(name -> {
            if (Validator.OLD_KEYWORDS_DRAFT_2019_09.contains(name)) {
              addIssue.accept("\"" + name + "\" was removed in Draft 2019-09");
            }
          });
        } else {  // Before Draft 2019-09
          object.keySet().forEach(name -> {
            if (Validator.NEW_KEYWORDS_DRAFT_2019_09.contains(name)) {
              addIssue.accept("\"" + name + "\" was added in Draft 2019-09");
            }
          });
        }

        if (state.spec().ordinal() < Specification.DRAFT_07.ordinal()) {
          object.keySet().forEach(name -> {
            if (Validator.NEW_KEYWORDS_DRAFT_07.contains(name)) {
              addIssue.accept("\"" + name + "\" was added in Draft-07");
            }
          });
        }
      }

      // Schema-specific keyword behaviour
      if (state.spec() == null || state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        if (object.has(MinContains.NAME)) {
          if (!object.has(Contains.NAME)) {
            addIssue.accept("\"" + MinContains.NAME + "\" without \"" + Contains.NAME + "\"");
          }
        }
        if (object.has(MaxContains.NAME)) {
          if (!object.has(Contains.NAME)) {
            addIssue.accept("\"" + MaxContains.NAME + "\" without \"" + Contains.NAME + "\"");
          }
        }
        if (object.has(UnevaluatedItems.NAME)) {
          JsonElement items = object.get(Items.NAME);
          if (items != null && !items.isJsonArray()) {
            addIssue.accept("\"" + UnevaluatedItems.NAME +
                            "\" without array-form \"" +
                            Items.NAME + "\"");
          }
        }

        // Minimum > maximum checks for this draft
        addIssue.accept(compareMinMax(object, MinContains.NAME, MaxContains.NAME));

        // Type checks for this draft
        addIssue.accept(checkType(object, ContentSchema.NAME, List.of("string")));
        addIssue.accept(checkType(object, DependentRequired.NAME, List.of("object")));
        addIssue.accept(checkType(object, DependentSchemas.NAME, List.of("object")));
        addIssue.accept(checkType(object, MaxContains.NAME, List.of("array")));
        addIssue.accept(checkType(object, MinContains.NAME, List.of("array")));
        addIssue.accept(checkType(object, UnevaluatedItems.NAME, List.of("array")));
        addIssue.accept(checkType(object, UnevaluatedProperties.NAME, List.of("object")));
      }

      if (state.spec() == null || state.spec().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
        // Type checks for this draft
        addIssue.accept(checkType(object, Dependencies.NAME, List.of("object")));
      }

      if (state.spec() == null || state.spec().ordinal() >= Specification.DRAFT_07.ordinal()) {
        if (object.has("then")) {
          if (!object.has(If.NAME)) {
            addIssue.accept("\"then\" without \"" + If.NAME + "\"");
          }
        }
        if (object.has("else")) {
          if (!object.has(If.NAME)) {
            addIssue.accept("\"else\" without \"" + If.NAME + "\"");
          }
        }

        // Type checks for this draft
        addIssue.accept(checkType(object, ContentEncoding.NAME, List.of("string")));
        addIssue.accept(checkType(object, ContentMediaType.NAME, List.of("string")));
      }

      // Unknown keywords, but not inside $vocabulary
      if (!is(path, CoreVocabulary.NAME)) {
        object.keySet().forEach(name -> {
          if (!KNOWN_KEYWORDS.contains(name)) {
            addIssue.accept("unknown keyword: \"" + Strings.jsonString(name) + "\"");
          }
        });
      }
    });

    return issues;
  }

  /**
   * Compare the specified "minimum" and "maximum" elements and returns any
   * issue message. This will return {@code null} if there is no issue.
   * <p>
   * This will return an issue if {@code min > max}.
   *
   * @param o the JSON object
   * @param minName the "minimum" element name
   * @param maxName the "maximum" element name
   * @return an issue message, or {@code null} for no issue.
   */
  private static String compareMinMax(JsonObject o, String minName, String maxName) {
    if (!o.has(minName) || !o.has(maxName)) {
      return null;
    }
    JsonElement minE = o.get(minName);
    JsonElement maxE = o.get(maxName);
    if (JSON.isNumber(minE) && JSON.isNumber(maxE)) {
      BigDecimal min = Numbers.valueOf(minE.getAsString());
      BigDecimal max = Numbers.valueOf(maxE.getAsString());
      if (min.compareTo(max) > 0) {
        return "\"" + minName + "\" > \"" + maxName + "\"";
      }
    }
    return null;
  }

  /**
   * Compare the specified "exclusive minimum" and "exclusive maximum" elements
   * and returns any issue message. This will return {@code null} if there is
   * no issue.
   * <p>
   * This will return an issue if {@code min >= max}.
   *
   * @param o the JSON object
   * @param minName the "minimum" element name
   * @param maxName the "maximum" element name
   * @return an issue message, or {@code null} for no issue.
   */
  private static String compareExclusiveMinMax(JsonObject o, String minName, String maxName) {
    if (!o.has(minName) || !o.has(maxName)) {
      return null;
    }
    JsonElement minE = o.get(minName);
    JsonElement maxE = o.get(maxName);
    if (JSON.isNumber(minE) && JSON.isNumber(maxE)) {
      BigDecimal min = Numbers.valueOf(minE.getAsString());
      BigDecimal max = Numbers.valueOf(maxE.getAsString());
      if (min.compareTo(max) >= 0) {
        return "\"" + minName + "\" >= \"" + maxName + "\"";
      }
    }
    return null;
  }

  /**
   * Checks that the given element has an expected sibling "type".
   *
   * @param o the JSON object
   * @param name the element name
   * @param expected a list of possible expected type values
   * @return an issue message, or {@code null} for no issue.
   */
  private static String checkType(JsonObject o, String name, List<String> expected) {
    if (!o.has(name)) {
      return null;
    }
    JsonElement type = o.get(Type.NAME);
    if (type == null) {
      return "\"" + name + "\" with no \"" + Type.NAME + "\"";
    }

    String want;
    String got;
    if (JSON.isString(type)) {
      if (expected.contains(type.getAsString())) {
        return null;
      }
      got = "\"" + type.getAsString() + "\"";
    } else if (type.isJsonArray()) {
      boolean allStrings = true;
      for (var e : type.getAsJsonArray()) {
        if (!JSON.isString(e)) {
          allStrings = false;
        } else {
          if (expected.contains(e.getAsString())) {
            return null;
          }
        }
      }
      if (allStrings) {
        got = StreamSupport.stream(type.getAsJsonArray().spliterator(), false)
            .map(e -> "\"" + e.getAsString() + "\"")
            .collect(Collectors.toList()).toString();
      } else {
        got = "mixed types";
      }
    } else {
      return "\"" + name + "\" with no valid \"" + Type.NAME + "\"";
    }

    if (expected.size() == 1) {
      want = "\"" + expected.get(0) + "\"";
    } else {
      want = "one of " +
             expected.stream().map(s -> "\"" + s + "\"").collect(Collectors.toList());
    }
    return "\"" + name + "\" type: want " + want + ", got " + got;
  }
}
