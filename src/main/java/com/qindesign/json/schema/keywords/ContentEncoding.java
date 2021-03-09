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
 * Created by shawn on 5/9/20 11:29 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Option;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "contentEncoding" annotation.
 */
public class ContentEncoding extends Keyword {
  public static final String NAME = "contentEncoding";

  public ContentEncoding() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().compareTo(Specification.DRAFT_07) < 0) {
      return true;
    }

    if (!JSON.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    // TODO: Collect annotation anyway?
    if (!JSON.isString(instance)) {
      return true;
    }

    context.addAnnotation(NAME, value.getAsString());

    // Only Draft-07 can make this a validation assertion
    if (context.specification() == Specification.DRAFT_07) {
      if (context.isOption(Option.CONTENT)) {
        if (value.getAsString().equalsIgnoreCase("base64")) {
          if (!Strings.isBase64(instance.getAsString())) {
            return false;
          }
        }
      }
    }

    return true;
  }
}
