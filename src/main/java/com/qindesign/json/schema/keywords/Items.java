/*
 * Created by shawn on 5/1/20 1:13 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidationResult;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "items" applicator.
 */
public class Items extends Keyword {
  public static final String NAME = "items";

  public Items() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isSchema(value) && !value.isJsonArray()) {
      context.schemaError("not a schema or array");
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonArray()) {
      return true;
    }

    JsonArray array = instance.getAsJsonArray();

    if (value.isJsonArray()) {
      JsonArray schemaArray = value.getAsJsonArray();
      int limit = Math.min(schemaArray.size(), array.size());
      for (int i = 0; i < limit; i++) {
        if (!context.apply(schemaArray.get(i), Integer.toString(i),
                           array.get(i), Integer.toString(i))) {
          context.addAnnotation(
              "error",
              new ValidationResult(false, "item " + i + " not valid in array"));
          return false;
        }
      }
      context.addAnnotation(Items.NAME, limit);
    } else {
      int index = 0;
      for (JsonElement e : array) {
        if (!context.apply(value, "", e, Integer.toString(index))) {
          context.addAnnotation(
              "error",
              new ValidationResult(false, "item " + index + " not valid"));
          return false;
        }
        index++;
      }
      context.addAnnotation(Items.NAME, true);
    }

    return true;
  }
}
