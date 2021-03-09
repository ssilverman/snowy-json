/*
 * Snow, a JSON Schema validator
 * Copyright (c) 2020-2021  Shawn Silverman
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
 * Created by shawn on 5/1/20 1:13 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "items" applicator.
 */
public class Items extends Keyword {
  public static final String NAME = "items";

  public Items() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isSchema(value) && !value.isJsonArray()) {
      context.schemaError("not a schema or array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonArray()) {
      return true;
    }

    JsonArray array = instance.getAsJsonArray();

    // Should we trust user input not being too huge and collect all the
    // invalid indexes?
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    if (value.isJsonArray()) {
      JsonArray schemaArray = value.getAsJsonArray();
      int limit = Math.min(schemaArray.size(), array.size());
      for (int i = 0; i < limit; i++) {
        String path = Integer.toString(i);
        if (!context.apply(schemaArray.get(i), path, null, array.get(i), path)) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", ");
          } else {
            sb.append("invalid items in array: ");
          }
          sb.append(i);
          // Don't mark the context as not collecting sub-annotations
        }
      }
      // TODO: Produce an annotation if items is empty?
      if (0 < limit && limit < array.size()) {
        context.addAnnotation(NAME, limit - 1);
      } else {
        context.addAnnotation(NAME, true);
      }
    } else {
      int index = 0;
      for (JsonElement e : array) {
        if (!context.apply(value, null, null, e, Integer.toString(index))) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", ");
          } else {
            sb.append("invalid items: ");
          }
          sb.append(index);
          // Don't mark the context as not collecting sub-annotations
        }
        index++;
      }
      context.addAnnotation(NAME, true);
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
