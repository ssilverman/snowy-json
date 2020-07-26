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
 * Created by shawn on 5/3/20 7:26 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.JSONPath;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implements the "unevaluatedProperties" applicator.
 */
public class UnevaluatedProperties extends Keyword {
  public static final String NAME = "unevaluatedProperties";

  public UnevaluatedProperties() {
    super(NAME);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }
    if (!context.isCollectAnnotations()) {
      context.schemaError("annotations are not being collected");
      return false;
    }

    context.checkValidSchema(value);

    if (!instance.isJsonObject()) {
      return true;
    }
    JsonObject object = instance.getAsJsonObject();

    JSONPath parentPath = context.schemaParentLocation();
    Set<String> validated = new HashSet<>();

    Consumer<Map<JSONPath, Annotation>> f = (Map<JSONPath, Annotation> a) -> {
      if (validated.size() >= object.size()) {
        return;
      }
      for (var e : a.entrySet()) {
        if (!e.getValue().isValid()) {
          continue;
        }
        if (e.getKey().size() <= parentPath.size() || !e.getKey().startsWith(parentPath)) {
          continue;
        }
        if (e.getValue().value instanceof Set<?>) {
          validated.addAll((Set<String>) e.getValue().value);
        }
      }
    };

    f.accept(context.annotations(Properties.NAME));
    f.accept(context.annotations(PatternProperties.NAME));
    f.accept(context.annotations(AdditionalProperties.NAME));
    f.accept(context.annotations(NAME));

    // Assume the number of properties is not unreasonable
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    Set<String> thisValidated = new HashSet<>();
    if (validated.size() < object.size()) {
      for (var e : object.entrySet()) {
        if (validated.contains(e.getKey())) {
          continue;
        }
        if (!context.apply(value, null, null, e.getValue(), e.getKey())) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", \"");
          } else {
            sb.append("invalid unevaluated properties: \"");
          }
          sb.append(Strings.jsonString(e.getKey())).append('\"');
          // Don't mark the context as not collecting sub-annotations
        }
        thisValidated.add(e.getKey());
      }
    }

    context.addAnnotation(NAME, thisValidated);
    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
