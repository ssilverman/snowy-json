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
import com.qindesign.json.schema.keywords.AdditionalItems;
import com.qindesign.json.schema.keywords.Contains;
import com.qindesign.json.schema.keywords.CoreDefs;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.CoreRef;
import com.qindesign.json.schema.keywords.CoreSchema;
import com.qindesign.json.schema.keywords.Definitions;
import com.qindesign.json.schema.keywords.Format;
import com.qindesign.json.schema.keywords.If;
import com.qindesign.json.schema.keywords.Items;
import com.qindesign.json.schema.keywords.MaxContains;
import com.qindesign.json.schema.keywords.MinContains;
import com.qindesign.json.schema.keywords.Properties;
import com.qindesign.json.schema.keywords.Type;
import com.qindesign.json.schema.keywords.UnevaluatedItems;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
   * Convenience method that adds an issue to the map.
   *
   * @param issues the map of issues
   * @param path the path
   * @param msg the message
   */
  private static void addIssue(Map<String, List<String>> issues, String path, String msg) {
    issues.computeIfAbsent(path, k -> new ArrayList<>()).add(msg);
  }

  /**
   * Checks if the path has a parent with the name {@code parentName}.
   *
   * @param path the path
   * @param parentName the parent name to check
   * @return whether the parent is {@code parentName}.
   */
  private static boolean isInParent(String path, String parentName) {
    int index = path.lastIndexOf('/');
    if (index < 0) {
      return false;
    }
    return (path.substring(0, index).endsWith("/" + parentName));
  }

  /**
   * Checks the given schema and returns lists of any issues found for each
   * element in the tree. This returns a map of JSON Pointer locations to a
   * list of associated issue messages.
   *
   * @param schema the schema to check
   * @return the linter results, mapping locations to a list of issues.
   */
  public static Map<String, List<String>> check(JsonElement schema) {
    Map<String, List<String>> issues = new HashMap<>();

    JSON.traverse(schema, (e, parent, path, state) -> {
      if (e.isJsonNull()) {
        return;
      }

      if (e.isJsonPrimitive()) {
        if (!isInParent(path, Properties.NAME)) {
          if (path.endsWith("/" + Format.NAME)) {
            if (JSON.isString(e)) {
              if (!KNOWN_FORMATS.contains(e.getAsString())) {
                addIssue(issues, path,
                         "unknown format: \"" + Strings.jsonString(e.getAsString()) + "\"");
              }
            }
          } else if (path.endsWith("/" + CoreId.NAME)) {
            if (JSON.isString(e)) {
              try {
                URI id = URI.parse(e.getAsString());
                if (!id.normalize().equals(id)) {
                  addIssue(issues, path,
                           "unnormalized ID: \"" + Strings.jsonString(e.getAsString()) + "\"");
                }
              } catch (URISyntaxException ex) {
                // Ignore
              }
            }
          } else if (path.endsWith("/" + CoreRef.NAME)) {
            // Only examine $refs that are just fragments
            if (JSON.isString(e)) {
              String ref = e.getAsString();
              if (ref.startsWith("#")) {
                boolean first = true;
                JsonElement o = schema;
                for (String part : ref.substring(1).split("/", -1)) {
                  if (first) {
                    if (!part.isEmpty()) {
                      addIssue(issues, path, "bad JSON Pointer: \"" + ref + "\"");
                      break;
                    }
                    first = false;
                    continue;
                  }
                  if (!o.isJsonObject() || !o.getAsJsonObject().has(part)) {
                    addIssue(issues, path,
                             "reference not found: \"" + Strings.jsonString(ref) + "\"");
                    break;
                  }
                  o = o.getAsJsonObject().get(part);
                }
              }
            }
          }
        }

        return;
      }

      if (e.isJsonArray()) {
        JsonArray array = e.getAsJsonArray();

        if (path.endsWith("/" + Items.NAME)) {
          if (array.size() <= 0) {
            addIssue(issues, path, "empty items array");
          }
        }

        return;
      }

      JsonObject object = e.getAsJsonObject();

      // If we're in a properties object then don't look at the names
      if (path.endsWith("/" + Properties.NAME)) {
        object.keySet().forEach(name -> {
          if (name.startsWith("$")) {
            addIssue(issues, path,
                     "property name starts with '$': \"" + Strings.jsonString(name) + "\"");
          }
        });

        return;
      }

      if (object.has(CoreSchema.NAME)) {
        if (parent != null && !object.has(CoreId.NAME)) {
          addIssue(issues, path,
                   "\"" + CoreSchema.NAME +
                   "\" in subschema without sibling \"" +
                   CoreId.NAME + "\"");
        }
      }

      if (object.has(AdditionalItems.NAME)) {
        JsonElement items = object.get(Items.NAME);
        if (items == null || !items.isJsonArray()) {
          addIssue(issues, path, "\"" + AdditionalItems.NAME + "\" without array-form \"items\"");
        }
      }

      if (object.has(Format.NAME)) {
        JsonElement type = object.get(Type.NAME);
        if (type == null || !JSON.isString(type) || !type.getAsString().equals("string")) {
          addIssue(issues, path, "\"" + Format.NAME + "\" without declared string type");
        }
      }

      // Check specification-specific keyword presence
      if (state.spec() != null) {
        if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          object.keySet().forEach(name -> {
            if (Validator.OLD_KEYWORDS_DRAFT_2019_09.contains(name)) {
              addIssue(issues, path, "\"" + name + "\" was removed in Draft 2019-09");
            }
          });
        } else {  // Before Draft 2019-09
          object.keySet().forEach(name -> {
            if (Validator.NEW_KEYWORDS_DRAFT_2019_09.contains(name)) {
              addIssue(issues, path, "\"" + name + "\" was added in Draft 2019-09");
            }
          });
        }

        if (state.spec().ordinal() < Specification.DRAFT_07.ordinal()) {
          object.keySet().forEach(name -> {
            if (Validator.NEW_KEYWORDS_DRAFT_07.contains(name)) {
              addIssue(issues, path, "\"" + name + "\" was added in Draft-07");
            }
          });
        }
      }

      boolean inDefs;  // Note: This is only a rudimentary check
      if (state.spec() != null) {
        if (state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
          inDefs = path.endsWith("/" + CoreDefs.NAME);
        } else {
          inDefs = path.endsWith("/" + Definitions.NAME);
        }
      } else {
        inDefs = path.endsWith("/" + CoreDefs.NAME) ||
                 path.endsWith("/" + Definitions.NAME);
      }

      // Allow anything in defs
      if (inDefs) {
        return;
      }

      // Schema-specific keyword behaviour
      if (state.spec() == null || state.spec().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        if (object.has(MinContains.NAME)) {
          if (!object.has(Contains.NAME)) {
            addIssue(issues, path,
                     "\"" + MinContains.NAME + "\" without \"" + Contains.NAME + "\"");
          }
        }
        if (object.has(MaxContains.NAME)) {
          if (!object.has(Contains.NAME)) {
            addIssue(issues, path,
                     "\"" + MaxContains.NAME + "\" without \"" + Contains.NAME + "\"");
          }
        }
        if (object.has(UnevaluatedItems.NAME)) {
          JsonElement items = object.get(Items.NAME);
          if (items != null && !items.isJsonArray()) {
            addIssue(issues, path,
                     "\"" + UnevaluatedItems.NAME +
                     "\" without array-form \"" +
                     Items.NAME + "\"");
          }
        }
      }

      if (state.spec() == null || state.spec().ordinal() >= Specification.DRAFT_07.ordinal()) {
        if (object.has("then")) {
          if (!object.has(If.NAME)) {
            addIssue(issues, path, "\"then\" without \"" + If.NAME + "\"");
          }
        }
        if (object.has("else")) {
          if (!object.has(If.NAME)) {
            addIssue(issues, path, "\"else\" without \"" + If.NAME + "\"");
          }
        }
      }

      // Unknown keywords, but not inside defs
      object.keySet().forEach(name -> {
        if (!KNOWN_KEYWORDS.contains(name)) {
          addIssue(issues, path, "unknown keyword: \"" + Strings.jsonString(name) + "\"");
        }
      });
    });

    return issues;
  }
}
