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
 * Created by shawn on 5/1/20 12:54 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "oneOf" applicator.
 */
public class OneOf extends Keyword {
  public static final String NAME = "oneOf";

  public OneOf() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
      context.schemaError("not a non-empty array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    // Let's assume that there aren't an unreasonable number of subschemas
    StringBuilder sb = new StringBuilder();

    int validCount = 0;
    int index = 0;
    for (JsonElement e : value.getAsJsonArray()) {
      String name = Integer.toString(index);
      if (context.apply(e, name, null, instance, null)) {
        validCount++;
        if (validCount > 1) {
          if (context.isFailFast()) {
            return false;
          }
        }
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(index);
        context.setCollectSubAnnotations(false);
      }
      index++;
    }

    if (validCount != 1) {
      if (sb.length() > 0) {
        context.addError(false, "want 1 subschema valid, got " + validCount + ": " + sb);
      } else {
        context.addError(false, "want 1 subschema valid, got " + validCount);
      }
      return false;
    }
    return true;
  }
}
