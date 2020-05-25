/*
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Numbers;
import com.qindesign.json.schema.Strings;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    Iterable<JsonElement> values;
    if (value.isJsonArray()) {
      values = value.getAsJsonArray();
      // Don't do all the schema validation here because it should have been
      // checked when validating the schema using the meta-schema
    } else if (JSON.isString(value)) {
      values = Collections.singleton(value.getAsJsonPrimitive());
    } else {
      context.schemaError("not an array or string");
      return false;
    }

    int index = 0;
    for (JsonElement t : values) {
      if (!JSON.isString(t)) {
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
          if (JSON.isBoolean(instance)) {
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
          if (JSON.isNumber(instance)) {
            return true;
          }
          break;
        case "integer":
          if (JSON.isNumber(instance)) {
            BigDecimal n = Numbers.valueOf(instance.getAsString());
            return n.stripTrailingZeros().scale() <= 0;
          }
          break;
        case "string":
          if (JSON.isString(instance)) {
            return true;
          }
          break;
      }
      index++;
    }

    if (JSON.isString(value)) {
      context.addError(false, "value not a \"" + Strings.jsonString(value.getAsString()) + "\"");
    } else {
      StringBuilder sb = new StringBuilder();
      value.getAsJsonArray()
          .forEach(e -> sb.append(", \"").append(Strings.jsonString(e.getAsString())).append('\"'));
      if (sb.length() == 0) {
        sb.append("[]");
      } else {
        sb.append(']').replace(0, 2, "[");
      }
      context.addError(false, "value not one of " + sb);
    }
    return false;
  }
}
