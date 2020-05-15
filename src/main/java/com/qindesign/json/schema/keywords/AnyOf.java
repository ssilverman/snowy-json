/*
 * Created by shawn on 5/1/20 12:54 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
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
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
      context.schemaError("not a non-empty array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    boolean retval = false;
    int index = 0;

    // Apply all of them to collect all annotations
    for (JsonElement e : value.getAsJsonArray()) {
      if (context.apply(e, Integer.toString(index++), instance, "")) {
        retval = true;
      }
    }

    if (!retval) {
      context.addError(false, "no items valid");
    }
    return retval;
  }
}
