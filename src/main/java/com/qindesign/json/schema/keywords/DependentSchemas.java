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
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
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

    boolean retval = true;

    JsonObject object = instance.getAsJsonObject();
    for (var e : value.getAsJsonObject().entrySet()) {
      if (!object.has(e.getKey())) {
        continue;
      }
      if (!context.apply(e.getValue(), e.getKey(), instance, "")) {
        if (context.isFailFast()) {
          return false;
        }
        context.addError(
            false,
            "dependent property \"" + Strings.jsonString(e.getKey()) + "\" not valid");
        retval = false;
        context.setCollectSubAnnotations(false);
      }
    }
    return retval;
  }
}
