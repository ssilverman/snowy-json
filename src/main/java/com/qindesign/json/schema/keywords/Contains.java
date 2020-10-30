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
 * Created by shawn on 5/1/20 12:01 AM.
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
 * Implements the "contains" applicator.
 */
public class Contains extends Keyword {
  public static final String NAME = "contains";

  public Contains() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonArray()) {
      return true;
    }

    int validCount = 0;
    int index = 0;

    // Apply all of them to collect all annotations
    for (JsonElement e : instance.getAsJsonArray()) {
      if (context.apply(value, null, null, e, Integer.toString(index++))) {
        validCount++;
      }
    }

    // Special handling if there's a minContains == 0
    boolean allowZero = false;
    if (context.specification().compareTo(Specification.DRAFT_2019_09) >= 0) {
      JsonElement minContains = parent.get(MinContains.NAME);
      if (minContains != null && JSON.isNumber(minContains)) {
        BigDecimal n = Numbers.valueOf(minContains.getAsString());
        if (n.signum() == 0) {
          allowZero = true;
        }
      }
    }

    if (allowZero || validCount > 0) {
      context.addLocalAnnotation(NAME, validCount);
      return true;
    }
    return false;
  }
}
