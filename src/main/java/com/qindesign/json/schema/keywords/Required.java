/*
 * Created by shawn on 5/1/20 12:21 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "required" assertion.
 */
public class Required extends Keyword {
  public static final String NAME = "required";

  public Required() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!instance.isJsonObject()) {
      return true;
    }

    JsonObject object = instance.getAsJsonObject();
    for (JsonElement e : value.getAsJsonArray()) {
      if (!object.has(e.getAsString())) {
        return false;
      }
    }
    return true;
  }
}
