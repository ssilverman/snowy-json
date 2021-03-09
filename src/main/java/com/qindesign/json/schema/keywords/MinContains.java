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
 * Created by shawn on 5/3/20 11:40 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Numbers;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;

import java.math.BigDecimal;

/**
 * Implements the "minContains" assertion.
 */
public class MinContains extends Keyword {
  public static final String NAME = "minContains";

  public MinContains() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().compareTo(Specification.DRAFT_2019_09) < 0) {
      return true;
    }

    if (!JSON.isNumber(value)) {
      context.schemaError("not a number");
      return false;
    }
    BigDecimal n = Numbers.valueOf(value.getAsString());
    if (n.signum() < 0) {
      context.schemaError("not >= 0");
      return false;
    }
    if (!Numbers.isInteger(n)) {
      context.schemaError("not an integer");
      return false;
    }

    if (!parent.has(Contains.NAME)) {
      return true;
    }

    Object containsA = context.localAnnotation(Contains.NAME);
    if (!(containsA instanceof Integer)) {
      return true;
    }

    BigDecimal v = BigDecimal.valueOf(((Integer) containsA).longValue());
    if (n.compareTo(v) > 0) {
      context.addError(false, "want at least " + n + " contains, got " + v);
      return false;
    }
    return true;
  }
}
