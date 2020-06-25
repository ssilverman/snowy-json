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
 * Created by shawn on 5/1/20 12:16 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "additionalItems" applicator.
 */
public class AdditionalItems extends Keyword {
  public static final String NAME = "additionalItems";

  public AdditionalItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    JsonElement items = parent.get(Items.NAME);
    if (items == null || items.isJsonObject()) {
      return true;
    }
    if (!items.isJsonArray()) {
      // Let the Items keyword validate
      return true;
    }

    if (!instance.isJsonArray()) {
      return true;
    }

    JsonArray schemaArray = items.getAsJsonArray();
    JsonArray array = instance.getAsJsonArray();

    int processedCount = Math.min(schemaArray.size(), array.size());

    // Old: Use a count instead of collecting which items failed because we
    // can't trust user input
    // New: Should we trust user input not being too huge and collect all the
    // invalid indexes?
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    for (int i = processedCount; i < array.size(); i++) {
      if (!context.apply(value, null, null, array.get(i), Integer.toString(i))) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", ");
        } else {
          sb.append("invalid additional items: ");
        }
        sb.append(i);
        context.setCollectSubAnnotations(false);
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }

    // Don't produce annotations if this keyword is not applied
    // TODO: Verify this restriction
    if (processedCount < array.size()) {
      context.addAnnotation(NAME, true);
    }
    return true;
  }
}
