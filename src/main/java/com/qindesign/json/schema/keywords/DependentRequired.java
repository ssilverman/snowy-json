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
 * Created by shawn on 5/1/20 12:21 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.JSONPath;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements the "dependentRequired" assertion.
 */
public class DependentRequired extends Keyword {
  public static final String NAME = "dependentRequired";

  public DependentRequired() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonObject()) {
      return true;
    }

    // Assume the number of properties is not unreasonable
    StringBuilder sb = new StringBuilder();

    JsonObject object = instance.getAsJsonObject();
    for (var e : value.getAsJsonObject().entrySet()) {
      if (!e.getValue().isJsonArray()) {
        context.schemaError(e.getKey() + ": not an array");
        return false;
      }
      if (!object.has(e.getKey())) {
        continue;
      }

      int index = 0;
      Set<String> names = new HashSet<>();
      for (JsonElement name : e.getValue().getAsJsonArray()) {
        if (!JSON.isString(name)) {
          context.schemaError(
              "not a string",
              JSONPath.fromElement(e.getKey()).append(Integer.toString(index)));
          return false;
        }
        if (!names.add(name.getAsString())) {
          context.schemaError(
              "\"" + Strings.jsonString(name.getAsString()) + "\": not unique",
              JSONPath.fromElement(e.getKey()).append(Integer.toString(index)));
          return false;
        }
        if (!object.has(name.getAsString())) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", \"");
          } else {
            sb.append("missing dependent properties: \"");
          }
          sb.append(Strings.jsonString(name.getAsString())).append('\"');
          context.setCollectSubAnnotations(false);
        }
        index++;
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
