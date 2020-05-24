/*
 * Created by shawn on 5/1/20 1:07 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "dependentSchemas" applicator.
 */
public class DependentSchemas extends Keyword {
  public static final String NAME = "dependentSchemas";

  public DependentSchemas() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }

    if (!instance.isJsonObject()) {
      return true;
    }

    // Assume the number of properties is not unreasonable
    StringBuilder sb = new StringBuilder();

    JsonObject object = instance.getAsJsonObject();
    for (var e : value.getAsJsonObject().entrySet()) {
      if (!object.has(e.getKey())) {
        continue;
      }
      if (!context.apply(e.getValue(), e.getKey(), null, instance, "")) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", \"");
        } else {
          sb.append("invalid dependent properties: \"");
        }
        sb.append(Strings.jsonString(e.getKey())).append('\"');
        context.setCollectSubAnnotations(false);
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
