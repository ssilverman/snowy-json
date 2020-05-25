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
import com.qindesign.json.schema.ValidatorContext;
import java.math.BigDecimal;

/**
 * Implements the "minimum" assertion.
 */
public class Minimum extends Keyword {
  public static final String NAME = "minimum";

  public Minimum() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!JSON.isNumber(value)) {
      context.schemaError("not a number");
      return false;
    }

    if (!JSON.isNumber(instance)) {
      return true;
    }

    BigDecimal n = Numbers.valueOf(instance.getAsString());
    BigDecimal v = Numbers.valueOf(value.getAsString());
    if (n.compareTo(v) < 0) {
      context.addError(false, "want at least " + v + ", got " + n);
      return false;
    }
    return true;
  }
}
