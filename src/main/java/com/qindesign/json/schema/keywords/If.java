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
 * Created by shawn on 5/2/20 10:45 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "if"/"then"/"else" applicators.
 */
public class If extends Keyword {
  public static final String NAME = "if";

  public If() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);
    if (context.specification().ordinal() < Specification.DRAFT_07.ordinal()) {
      return true;
    }

    JsonElement thenElem = parent.get("then");
    if (thenElem != null) {
      context.checkValidSchema(thenElem, "../then");
    }
    JsonElement elseElem = parent.get("else");
    if (elseElem != null) {
      context.checkValidSchema(elseElem, "../else");
    }

    // TODO: Normalize the schema paths
    if (context.apply(value, "", null, instance, "")) {
      if (thenElem == null) {
        return true;
      }
      return context.apply(thenElem, "../then", null, instance, "");
    } else {
      if (elseElem == null) {
        return true;
      }
      return context.apply(elseElem, "../else", null, instance, "");
    }
  }
}
