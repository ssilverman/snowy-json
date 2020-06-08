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
 * Created by shawn on 5/3/20 7:24 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Option;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;
import java.util.Map;
import java.util.function.Function;

/**
 * Implements the "unevaluatedItems" applicator.
 */
public class UnevaluatedItems extends Keyword {
  public static final String NAME = "unevaluatedItems";

  public UnevaluatedItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }
    if (!context.isOption(Option.COLLECT_ANNOTATIONS)) {
      context.schemaError("annotations are not being collected");
      return false;
    }

    context.checkValidSchema(value);

    if (!instance.isJsonArray()) {
      return true;
    }

    String parentPrefix = context.schemaParentLocation() + "/";
    int max = 0;

    // Returns true if we need to return true and false to not return
    Function<Map<String, Annotation>, Boolean> f = (Map<String, Annotation> a) -> {
      for (var e : a.entrySet()) {
        if (!e.getKey().startsWith(parentPrefix)) {
          continue;
        }
        if (Boolean.TRUE.equals(e.getValue().value)) {
          return true;
        }
      }
      return false;
    };

    if (f.apply(context.annotations(AdditionalItems.NAME))) {
      return true;
    }
    if (f.apply(context.annotations(UnevaluatedItems.NAME))) {
      return true;
    }

    // "items"
    Map<String, Annotation> annotations = context.annotations(Items.NAME);
    for (var e : annotations.entrySet()) {
      if (!e.getKey().startsWith(parentPrefix)) {
        continue;
      }
      Object v = e.getValue().value;
      if (v == null) {
        continue;
      }
      if (v.equals(true)) {
        return true;
      }
      if (v instanceof Integer) {
        max = Math.max(max, (Integer) v + 1);
      }
    }

    // Assume the number of items is not unreasonable
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    JsonArray array = instance.getAsJsonArray();
    for (int i = max; i < array.size(); i++) {
      if (!context.apply(value, "", null, array.get(i), Integer.toString(i))) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", ");
        } else {
          sb.append("invalid unevaluated items: ");
        }
        sb.append(i);
        context.setCollectSubAnnotations(false);
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }

    context.addAnnotation(NAME, true);
    return true;
  }
}
