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
 * Created by shawn on 5/1/20 3:18 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the "patternProperties" applicator.
 */
public class PatternProperties extends Keyword {
  public static final String NAME = "patternProperties";

  public PatternProperties() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonObject()) {
      return true;
    }

    JsonObject schemaObject = value.getAsJsonObject();
    JsonObject object = instance.getAsJsonObject();

    // Compile all and check all the schema patterns
    // We need a pair: the regex and its ECMA-262-translated value
    List<Map.Entry<String, java.util.regex.Pattern>> patterns =
        new ArrayList<>(schemaObject.size());
    for (var e : schemaObject.entrySet()) {
      try {
        patterns.add(Map.entry(e.getKey(), context.pattern(e.getKey())));
      } catch (PatternSyntaxException ex) {
        // Technically, this is a "SHOULD" and not a "MUST"
        context.schemaError("not a valid pattern", e.getKey());
        return false;
      }
      context.checkValidSchema(e.getValue(), e.getKey());
    }

    // Assume the number of properties is not unreasonable
    StringBuilder sb = new StringBuilder();

    Set<String> validated = new HashSet<>();
    for (var e : object.entrySet()) {
      // For each that matches, check the schema
      for (Map.Entry<String, java.util.regex.Pattern> p : patterns) {
        if (!p.getValue().matcher(e.getKey()).find()) {
          continue;
        }
        if (!context.apply(schemaObject.get(p.getKey()), p.getKey(), null,
                           e.getValue(), e.getKey())) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", \"");
          } else {
            sb.append("invalid properties: \"");
          }
          sb.append(Strings.jsonString(e.getKey())).append("\" matches \"")
              .append(Strings.jsonString(p.getKey())).append('\"');
          // Don't mark the context as not collecting sub-annotations
        }
        validated.add(e.getKey());
      }
    }

    context.addAnnotation(NAME, validated);
    context.addLocalAnnotation(NAME, validated);
    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
