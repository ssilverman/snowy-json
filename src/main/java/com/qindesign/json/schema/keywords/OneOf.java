/*
 * Created by shawn on 5/1/20 12:54 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
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
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
      context.schemaError("not a non-empty array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    int validCount = 0;
    int index = 0;
    for (JsonElement e : value.getAsJsonArray()) {
      if (context.apply(e, Integer.toString(index++), instance, "")) {
        validCount++;
      }
    }
    return validCount == 1;
  }
}
