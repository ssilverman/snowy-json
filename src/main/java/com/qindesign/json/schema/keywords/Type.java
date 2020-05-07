/*
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Numbers;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.math.BigDecimal;
import java.util.Collections;

/**
 * Implements the "type" assertion.
 */
public class Type extends Keyword {
  public static final String NAME = "type";

  public Type() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    Iterable<JsonElement> values;
    if (value.isJsonArray()) {
      values = value.getAsJsonArray();
      // Don't do all the schema validation here because it should have been
      // checked when validating the schema using the meta-schema
    } else if (Validator.isString(value)) {
      values = Collections.singleton(value.getAsJsonPrimitive());
    } else {
      context.schemaError("not an array or string");
      return false;
    }

    int index = 0;
    for (JsonElement t : values) {
      if (!Validator.isString(t)) {
        context.schemaError("not a string", Integer.toString(index));
        return false;
      }
      switch (t.getAsString()) {
        case "null":
          if (instance.isJsonNull()) {
            return true;
          }
          break;
        case "boolean":
          if (Validator.isBoolean(instance)) {
            return true;
          }
          break;
        case "object":
          if (instance.isJsonObject()) {
            return true;
          }
          break;
        case "array":
          if (instance.isJsonArray()) {
            return true;
          }
          break;
        case "number":
          if (Validator.isNumber(instance)) {
            return true;
          }
          break;
        case "integer":
          if (Validator.isNumber(instance)) {
            BigDecimal n = Numbers.valueOf(instance.getAsString());
            return n.stripTrailingZeros().scale() <= 0;
          }
          break;
        case "string":
          if (Validator.isString(instance)) {
            return true;
          }
          break;
      }
      index++;
    }
    return false;
  }
}
