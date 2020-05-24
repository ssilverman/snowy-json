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
 * Implements the "oneOf" applicator.
 */
public class OneOf extends Keyword {
  public static final String NAME = "oneOf";

  public OneOf() {
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

    // Let's assume that there aren't an unreasonable number of subschemas
    StringBuilder sb = new StringBuilder();

    int validCount = 0;
    int index = 0;
    for (JsonElement e : value.getAsJsonArray()) {
      String path = Integer.toString(index);
      if (context.apply(e, path, null, instance, "")) {
        validCount++;
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(index);
      }
      if (validCount > 1) {
        context.setCollectSubAnnotations(false);
      }
      index++;
    }

    if (validCount != 1) {
      if (sb.length() > 0) {
        context.addError(false, "want 1 subschema valid, got " + validCount + ": " + sb);
      } else {
        context.addError(false, "want 1 subschema valid, got " + validCount);
      }
      return false;
    }
    return true;
  }
}
