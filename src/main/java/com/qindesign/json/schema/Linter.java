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
import com.google.gson.JsonParseException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A rudimentary linter. This program takes one argument:
 * <ol>
 * <li>Schema path or URL</li>
 * </ol>
 */
public final class Linter {
  private static final Class<?> CLASS = Linter.class;

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

  /**
   * Main program entry point.
   *
   * @param args the program arguments
   * @throws IOException if there was an error reading the file.
   * @throws JsonParseException if there was an error parsing the JSON.
   */
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
    Linter linter = new Linter();
    var issues = linter.check(schema);
    issues.forEach((path, list) -> list.forEach(msg -> System.out.println(path + ": " + msg)));
  }

  /**
   * String rules. These all assume the parent is not a "properties" and not a
   * definitions, depending on the specification.
   */
  private static final List<Consumer<Context>> STRING_RULES = List.of(
      context -> {
        if (context.is(Format.NAME)) {
          if (!KNOWN_FORMATS.contains(context.string())) {
            context.addIssue(
                "unknown format: \"" + Strings.jsonString(context.string()) + "\"");
          }
        }
      },
      context -> {
        if (context.is(CoreId.NAME)) {
          try {
            URI id = URI.parse(context.string());
            if (!id.normalize().equals(id)) {
              context.addIssue(
                  "unnormalized ID: \"" + Strings.jsonString(context.string()) + "\"");
            }
            if (context.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
              if (id.rawFragment() != null && id.rawFragment().isEmpty()) {
                context.addIssue(
                    "empty fragment: \"" + Strings.jsonString(context.string()) + "\"");
              }
            }
          } catch (URISyntaxException ex) {
            // Ignore
          }
        }
      },
      context -> {
        if (context.is(CoreRef.NAME)) {
          if (context.string().startsWith("#")) {
            JsonElement o = context.schema();
            JSONPath path = JSONPath.fromJSONPointer(context.string().substring(1));
            if (!path.isAbsolute()) {
              context.addIssue("bad JSON Pointer: \"" + context.string() + "\"");
            } else {
              for (String part : path) {
                if (!o.isJsonObject() || !o.getAsJsonObject().has(part)) {
                  context.addIssue(
                      "reference not found: \"" +
                      Strings.jsonString(context.string()) +
                      "\"");
                  break;
                }
                o = o.getAsJsonObject().get(part);
              }
            }
          }
        }
      }
  );

  /** Array rules. */
  private static final List<Consumer<Context>> ARRAY_RULES = List.of(
      context -> {
        if (context.is(Items.NAME)) {
          if (context.array().size() <= 0) {
            context.addIssue("empty items array");
          }
        }
      }
  );

  /**
   * Properties rules. These assume that the current element is a "properties".
   */
  private static final List<Consumer<Context>> PROPERTIES_RULES = List.of(
      context -> {
        context.object().keySet().forEach(name -> {
          if (name.startsWith("$")) {
            context.addIssue("property name starts with '$': \"" + Strings.jsonString(name) + "\"");
          }
        });
      }
  );

  /**
   * Object rules. These all assume the current element is not a "properties",
   * not a definitions, and not unknown.
   */
  private static final List<Consumer<Context>> OBJECT_RULES = List.of(
      context -> {
        if (context.object().has(CoreSchema.NAME)) {
          if (context.parent() != null && !context.object().has(CoreId.NAME)) {
            context.addIssue("\"" + CoreSchema.NAME +
                             "\" in subschema without sibling \"" +
                             CoreId.NAME + "\"");
          }
        }
      },
      context -> {
        if (context.object().has(AdditionalItems.NAME)) {
          JsonElement items = context.object().get(Items.NAME);
          if (items == null || !items.isJsonArray()) {
            context.addIssue("\"" + AdditionalItems.NAME + "\" without array-form \"items\"");
          }
        }
      },

      // Minimum > maximum checks for all drafts
      context -> {
        context.addIssue(compareExclusiveMinMax(context.object(), ExclusiveMinimum.NAME, ExclusiveMaximum.NAME));
        context.addIssue(compareMinMax(context.object(), Minimum.NAME, Maximum.NAME));
        context.addIssue(compareMinMax(context.object(), MinItems.NAME, MaxItems.NAME));
        context.addIssue(compareMinMax(context.object(), MinLength.NAME, MaxLength.NAME));
        context.addIssue(compareMinMax(context.object(), MinProperties.NAME, MaxProperties.NAME));
      },

      // Type checks for all drafts
      context -> {
        context.addIssue(checkType(context.object(), AdditionalItems.NAME, List.of("array")));
        context.addIssue(checkType(context.object(), AdditionalProperties.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), Contains.NAME, List.of("array")));
        context.addIssue(checkType(context.object(), ExclusiveMaximum.NAME, List.of("number", "integer")));
        context.addIssue(checkType(context.object(), ExclusiveMinimum.NAME, List.of("number", "integer")));
        context.addIssue(checkType(context.object(), Format.NAME, List.of("string")));
        context.addIssue(checkType(context.object(), Items.NAME, List.of("array")));
        context.addIssue(checkType(context.object(), Maximum.NAME, List.of("number", "integer")));
        context.addIssue(checkType(context.object(), MaxItems.NAME, List.of("array")));
        context.addIssue(checkType(context.object(), MaxLength.NAME, List.of("string")));
        context.addIssue(checkType(context.object(), MaxProperties.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), Minimum.NAME, List.of("number", "integer")));
        context.addIssue(checkType(context.object(), MinItems.NAME, List.of("array")));
        context.addIssue(checkType(context.object(), MinLength.NAME, List.of("string")));
        context.addIssue(checkType(context.object(), MinProperties.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), MultipleOf.NAME, List.of("number", "integer")));
        context.addIssue(checkType(context.object(), Pattern.NAME, List.of("string")));
        context.addIssue(checkType(context.object(), PatternProperties.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), Properties.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), PropertyNames.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), Required.NAME, List.of("object")));
        context.addIssue(checkType(context.object(), UniqueItems.NAME, List.of("array")));
      },

      // Check specification-specific keyword presence
      context -> {
        if (context.spec() != null) {
          if (context.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
            context.object().keySet().forEach(name -> {
              if (Validator.OLD_KEYWORDS_DRAFT_2019_09.contains(name)) {
                context.addIssue("\"" + name + "\" was removed in Draft 2019-09");
              }
            });
          } else {  // Before Draft 2019-09
            context.object().keySet().forEach(name -> {
              if (Validator.NEW_KEYWORDS_DRAFT_2019_09.contains(name)) {
                context.addIssue("\"" + name + "\" was added in Draft 2019-09");
              }
            });
          }

          if (context.spec().ordinal() < Specification.DRAFT_07.ordinal()) {
            context.object().keySet().forEach(name -> {
              if (Validator.NEW_KEYWORDS_DRAFT_07.contains(name)) {
                context.addIssue("\"" + name + "\" was added in Draft-07");
              }
            });
          }
        }
      },

      // Schema-specific keyword behaviour
      context -> {
        if (context.spec() == null || context.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          if (context.object().has(MinContains.NAME)) {
            if (!context.object().has(Contains.NAME)) {
              context.addIssue("\"" + MinContains.NAME + "\" without \"" + Contains.NAME + "\"");
            }
          }
          if (context.object().has(MaxContains.NAME)) {
            if (!context.object().has(Contains.NAME)) {
              context.addIssue("\"" + MaxContains.NAME + "\" without \"" + Contains.NAME + "\"");
            }
          }
          if (context.object().has(UnevaluatedItems.NAME)) {
            JsonElement items = context.object().get(Items.NAME);
            if (items != null && !items.isJsonArray()) {
              context.addIssue("\"" + UnevaluatedItems.NAME +
                               "\" without array-form \"" +
                               Items.NAME + "\"");
            }
          }

          // Minimum > maximum checks for this draft
          context.addIssue(compareMinMax(context.object(), MinContains.NAME, MaxContains.NAME));

          // Type checks for this draft
          context.addIssue(checkType(context.object(), ContentSchema.NAME, List.of("string")));
          context.addIssue(checkType(context.object(), DependentRequired.NAME, List.of("object")));
          context.addIssue(checkType(context.object(), DependentSchemas.NAME, List.of("object")));
          context.addIssue(checkType(context.object(), MaxContains.NAME, List.of("array")));
          context.addIssue(checkType(context.object(), MinContains.NAME, List.of("array")));
          context.addIssue(checkType(context.object(), UnevaluatedItems.NAME, List.of("array")));
          context.addIssue(checkType(context.object(), UnevaluatedProperties.NAME, List.of("object")));
        }

        if (context.spec() == null || context.spec().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
          // Type checks for this draft
          context.addIssue(checkType(context.object(), Dependencies.NAME, List.of("object")));
        }

        if (context.spec() == null || context.spec().ordinal() >= Specification.DRAFT_07.ordinal()) {
          if (context.object().has("then")) {
            if (!context.object().has(If.NAME)) {
              context.addIssue("\"then\" without \"" + If.NAME + "\"");
            }
          }
          if (context.object().has("else")) {
            if (!context.object().has(If.NAME)) {
              context.addIssue("\"else\" without \"" + If.NAME + "\"");
            }
          }

          // Type checks for this draft
          context.addIssue(checkType(context.object(), ContentEncoding.NAME, List.of("string")));
          context.addIssue(checkType(context.object(), ContentMediaType.NAME, List.of("string")));
        }
      },

      // Unknown keywords, but not inside $vocabulary
      context -> {
        if (!context.is(CoreVocabulary.NAME)) {
          context.object().keySet().forEach(name -> {
            if (!KNOWN_KEYWORDS.contains(name)) {
              context.addIssue("unknown keyword: \"" + Strings.jsonString(name) + "\"");
            }
          });
        }
      }
  );

  public static final class Context {
    private final Map<JSONPath, List<String>> issues;

    JsonElement schema;
    JsonElement element;
    JsonElement parent;
    JSONPath path;
    Specification spec;

    boolean parentIsProperties;
    boolean parentIsDefs;
    boolean isDefs;

    Context(Map<JSONPath, List<String>> issues) {
      this.issues = issues;
    }

    /**
     * Checks if the parent of the current element is "properties".
     *
     * @return this fact.
     */
    public boolean parentIsProperties() {
      return parentIsProperties;
    }

    /**
     * Checks if the parent of the current element is either "$defs" or
     * "definitions", depending on the current specification.
     *
     * @return this fact.
     */
    public boolean parentIsDefs() {
      return parentIsProperties;
    }

    /**
     * Checks if the current element is either "$defs" or "definitions",
     * depending on the current specification.
     *
     * @return this fact.
     */
    public boolean isDefs() {
      return isDefs;
    }

    /**
     * Gets the schema element.
     *
     * @return the schema element.
     */
    public JsonElement schema() {
      return schema;
    }

    /**
     * Gets the current element.
     *
     * @return the current element.
     */
    public JsonElement element() {
      return element;
    }

    /**
     * Gets the element as an object.
     *
     * @return the element as an object.
     * @throws IllegalStateException if the element is not an object.
     */
    public String string() {
      return element.getAsString();
    }

    /**
     * Gets the element as an object.
     *
     * @return the element as an object.
     * @throws IllegalStateException if the element is not an object.
     */
    public JsonObject object() {
      return element.getAsJsonObject();
    }

    /**
     * Gets the element as an array.
     *
     * @return the element as an object.
     * @throws IllegalStateException if the element is not an array.
     */
    public JsonArray array() {
      return element.getAsJsonArray();
    }

    /**
     * Gets the parent of the current element, may be {@code null} if there is
     * no parent.
     *
     * @return the parent of the current element, may be {@code null}.
     */
    public JsonElement parent() {
      return parent;
    }

    /**
     * Gets the path of the current element.
     *
     * @return the path of the current element.
     */
    public JSONPath path() {
      return path;
    }

    /**
     * Gets the specification, if known. This returns {@code null} if the
     * current specification is unknown.
     *
     * @return the current specification, if known, or {@code null} if unknown.
     */
    public Specification spec() {
      return spec;
    }

    /**
     * Checks if the current path has a parent with the given name.
     *
     * @param name the parent name to check
     * @return whether the parent has the given name.
     */
    public boolean hasParent(String name) {
      return path.size() >= 2 && path.get(path.size() - 2).equals(name);
    }

    /**
     * Checks if the current path ends with the given name.
     *
     * @param name the name to check
     * @return whether the path ends with the given name.
     */
    public boolean is(String name) {
      return !path.isEmpty() && path.get(path.size() - 1).equals(name);
    }

    /**
     * Checks if the current path ends with an unknown keyword. This returns
     * {@code false} if the parent of this element is an array.
     * <p>
     * This tests all known schema keywords in a non-specification-specific way.
     *
     * @return whether the last element is unknown and not a child of an array.
     */
    private boolean isUnknown() {
      if (path.isEmpty()) {
        return false;
      }
      if (parent != null && parent.isJsonArray()) {
        return false;
      }
      return !KNOWN_KEYWORDS.contains(path.get(path.size() - 1));
    }

    /**
     * Adds an issue to the current path. A {@code null} issue indicates that
     * there's no issue.
     *
     * @param issue the issue to add, or {@code null} for no issue
     */
    public void addIssue(String issue) {
      if (issue != null) {
        issues.computeIfAbsent(path, k -> new ArrayList<>()).add(issue);
      }
    }
  }

  // All the rules
  List<Consumer<Context>> nullRules = new ArrayList<>();
  List<Consumer<Context>> primitiveRules = new ArrayList<>();
  List<Consumer<Context>> stringRules = new ArrayList<>();
  List<Consumer<Context>> objectRules = new ArrayList<>();
  List<Consumer<Context>> arrayRules = new ArrayList<>();
  List<Consumer<Context>> otherRules = new ArrayList<>();

  /**
   * Creates a new linter.
   */
  public Linter() {
  }

  /**
   * Checks the given schema and returns lists of any issues found for each
   * element in the tree. This returns a map of JSON element paths to a list of
   * associated issue messages.
   *
   * @param schema the schema to check
   * @return the linter results, mapping paths to a list of issues.
   */
  public Map<JSONPath, List<String>> check(JsonElement schema) {
    // The issue list by location
    Map<JSONPath, List<String>> issues = new HashMap<>();
    Context context = new Context(issues);
    context.schema = schema;

    Consumer<List<Consumer<Context>>> processRules =
        list -> list.forEach(f -> f.accept(context));

    JSON.traverse(schema, (e, parent, path, state) -> {
      context.element = e;
      context.parent = parent;
      context.path = path;
      context.spec = state.spec();
      context.parentIsProperties = context.hasParent(Properties.NAME);

      if (state.spec() != null) {
        if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          context.isDefs = context.is(CoreDefs.NAME);
          context.parentIsDefs = context.hasParent(CoreDefs.NAME);
        } else {
          context.isDefs = context.is(Definitions.NAME);
          context.parentIsDefs = context.hasParent(Definitions.NAME);
        }
      } else {
        context.isDefs = context.is(CoreDefs.NAME) ||
                         context.is(Definitions.NAME);
        context.parentIsDefs = context.hasParent(CoreDefs.NAME) ||
                               context.hasParent(Definitions.NAME);
      }

      if (e.isJsonNull()) {
        processRules.accept(nullRules);
      } else if (e.isJsonPrimitive()) {
        processRules.accept(primitiveRules);
        if (e.getAsJsonPrimitive().isString()) {
          if (!context.parentIsProperties() && !context.parentIsDefs()) {
            processRules.accept(STRING_RULES);
          }
          processRules.accept(stringRules);
        }
      } else if (e.isJsonArray()) {
        processRules.accept(ARRAY_RULES);
        processRules.accept(arrayRules);
      } else if (e.isJsonObject()) {
        if (context.is(Properties.NAME)) {
          processRules.accept(PROPERTIES_RULES);
        } else {
          if (!context.isDefs() && (!context.isUnknown() || context.path().size() > 1)) {
            processRules.accept(OBJECT_RULES);
          }
        }
        processRules.accept(objectRules);
      }
      processRules.accept(otherRules);
    });

    return issues;
  }

  /**
   * Adds a rule where the element is guaranteed to be a JSON primitive but
   * not "null".
   *
   * @param rule the rule to add
   */
  public void addPrimitiveRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    primitiveRules.add(rule);
  }

  /**
   * Adds a rule where the element is guaranteed to be a JSON null.
   *
   * @param rule the rule to add
   */
  public void addNullRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    nullRules.add(rule);
  }

  /**
   * Adds a rule where the element is guaranteed to be a primitive string.
   *
   * @param rule the rule to add
   */
  public void addStringRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    stringRules.add(rule);
  }

  /**
   * Adds a rule where the element is guaranteed to be a JSON object.
   *
   * @param rule the rule to add
   */
  public void addObjectRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    objectRules.add(rule);
  }

  /**
   * Adds a rule where the element is guaranteed to be a JSON array.
   *
   * @param rule the rule to add
   */
  public void addArrayRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    arrayRules.add(rule);
  }

  /**
   * Adds a linter rule. There's no guarantees about the element type.
   *
   * @param rule the rule to add
   */
  public void addRule(Consumer<Context> rule) {
    Objects.requireNonNull(rule, "rule");
    otherRules.add(rule);
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
