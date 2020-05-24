/*
 * Created by shawn on 5/1/20 12:54 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "anyOf" applicator.
 */
public class AnyOf extends Keyword {
  public static final String NAME = "anyOf";

  public AnyOf() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
      context.schemaError("not a non-empty array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    int index = 0;

    // Let's assume that there aren't an unreasonable number of subschemas
    StringBuilder sb = new StringBuilder();

    // Apply all of them to collect all annotations
    for (JsonElement e : value.getAsJsonArray()) {
      String path = Integer.toString(index);
      if (context.apply(e, path, null, instance, "")) {
        if (sb.length() > 0) {
          sb.append(", ");
        } else {
          sb.append("valid subschemas: ");
        }
        sb.append(index);
      }
      index++;
    }

    if (sb.length() == 0) {
      context.addError(false, "no valid subschemas");
      return false;
    }

    context.addError(true, sb.toString());
    return true;
  }
}
