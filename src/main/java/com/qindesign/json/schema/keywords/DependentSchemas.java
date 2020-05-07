/*
 * Created by shawn on 5/1/20 1:07 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.util.Map;

/**
 * Implements the "dependentSchemas" applicator.
 */
public class DependentSchemas extends Keyword {
  public static final String NAME = "dependentSchemas";

  public DependentSchemas() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }

    if (!instance.isJsonObject()) {
      return true;
    }

    JsonObject object = instance.getAsJsonObject();
    for (var e : value.getAsJsonObject().entrySet()) {
      if (!object.has(e.getKey())) {
        continue;
      }
      if (!context.apply(e.getValue(), e.getKey(), instance, "")) {
        return false;
      }
    }
    return true;
  }
}
