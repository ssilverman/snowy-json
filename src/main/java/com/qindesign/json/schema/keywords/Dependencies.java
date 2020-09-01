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
 * Created by shawn on 5/10/20 1:47 AM.
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
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements "dependencies".
 */
public class Dependencies extends Keyword {
  public static final String NAME = "dependencies";

  public Dependencies() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().compareTo(Specification.DRAFT_2019_09) >= 0) {
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
    StringBuilder sbInvalid = new StringBuilder();
    StringBuilder sbNotFound = new StringBuilder();

    JsonObject object = instance.getAsJsonObject();
    for (var e : value.getAsJsonObject().entrySet()) {
      if (Validator.isSchema(e.getValue())) {
        if (!object.has(e.getKey())) {
          continue;
        }
        if (!context.apply(e.getValue(), e.getKey(), null, instance, null)) {
          if (context.isFailFast()) {
            return false;
          }
          if (sbInvalid.length() > 0) {
            sbInvalid.append(", \"");
          } else {
            sbInvalid.append("invalid dependent properties: \"");
          }
          sbInvalid.append(Strings.jsonString(e.getKey())).append('\"');
          // Don't mark the context as not collecting sub-annotations
        }
      } else if (e.getValue().isJsonArray()) {
        if (!object.has(e.getKey())) {
          continue;
        }

        int index = 0;
        Set<String> names = new HashSet<>();
        for (JsonElement name : e.getValue().getAsJsonArray()) {
          if (!JSON.isString(name)) {
            context.schemaError("not a string",
                                JSONPath.fromElement(e.getKey()).append(Integer.toString(index)));
            return false;
          }
          if (!names.add(name.getAsString())) {
            context.schemaError("\"" + Strings.jsonString(name.getAsString()) + "\": not unique",
                                JSONPath.fromElement(e.getKey()).append(Integer.toString(index)));
            return false;
          }
          if (!object.has(name.getAsString())) {
            if (context.isFailFast()) {
              return false;
            }
            if (sbNotFound.length() > 0) {
              sbNotFound.append(", \"");
            } else {
              sbNotFound.append("missing dependent properties: \"");
            }
            sbNotFound.append(Strings.jsonString(name.getAsString())).append('\"');
            // Don't mark the context as not collecting sub-annotations
          }
          index++;
        }
      } else {
        context.schemaError("not a schema or array", e.getKey());
        return false;
      }
    }

    boolean retval = true;
    if (sbInvalid.length() > 0 && sbNotFound.length() > 0) {
      context.addError(false, sbInvalid + "; " + sbNotFound);
      retval = false;
    } else if (sbInvalid.length() > 0) {
      context.addError(false, sbInvalid.toString());
      retval = false;
    } else if (sbNotFound.length() > 0) {
      context.addError(false, sbNotFound.toString());
      retval = false;
    }
    return retval;
  }
}
