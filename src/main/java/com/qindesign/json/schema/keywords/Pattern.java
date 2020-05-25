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
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the "pattern" assertion.
 */
public class Pattern extends Keyword {
  public static final String NAME = "pattern";

  public Pattern() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!JSON.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    java.util.regex.Pattern p;
    try {
      p = context.patternCache().access(value.getAsString());
    } catch (PatternSyntaxException ex) {
      // Technically, this is a "SHOULD" and not a "MUST"
      context.schemaError("not a valid pattern");
      return false;
    }

    if (!JSON.isString(instance)) {
      return true;
    }

    if (!p.matcher(instance.getAsString()).find()) {
      context.addError(
          false,
          "string \"" + Strings.jsonString(instance.getAsString()) +
          "\" does not match pattern \"" + Strings.jsonString(value.getAsString()) + "\"");
      return false;
    }
    return true;
  }
}
