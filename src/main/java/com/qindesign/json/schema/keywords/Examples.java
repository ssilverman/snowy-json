/*
 * Created by shawn on 5/5/20 10:47 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "examples" annotation.
 */
public class Examples extends Keyword {
  public static final String NAME = "examples";

  public Examples() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray()) {
      context.schemaError("not an array");
      return false;
    }

    context.addAnnotation(NAME, value.getAsJsonArray());
    return false;
  }
}
