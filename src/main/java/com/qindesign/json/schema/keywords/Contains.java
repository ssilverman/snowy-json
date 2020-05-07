/*
 * Created by shawn on 5/1/20 12:01 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "contains" applicator.
 */
public class Contains extends Keyword {
  public static final String NAME = "contains";

  public Contains() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonArray()) {
      return true;
    }

    int validCount = 0;
    int index = 0;

    // Apply all of them to collect all annotations
    for (JsonElement e : instance.getAsJsonArray()) {
      if (context.apply(value, "", e, Integer.toString(index++))) {
        validCount++;
      }
    }
    context.addAnnotation("contains", validCount);
    return validCount > 0;
  }
}
