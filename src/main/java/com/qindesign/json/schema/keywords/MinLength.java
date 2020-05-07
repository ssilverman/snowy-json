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

/**
 * Implements the "minLength" assertion.
 */
public class MinLength extends Keyword {
  public static final String NAME = "minLength";

  public MinLength() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isNumber(value)) {
      context.schemaError("not a number");
      return false;
    }
    BigDecimal n = Numbers.valueOf(value.getAsString());
    if (n.compareTo(BigDecimal.ZERO) < 0) {
      context.schemaError("not >= 0");
      return false;
    }

    if (!Validator.isString(instance)) {
      return true;
    }
    BigDecimal v = BigDecimal.valueOf(instance.getAsString().length());
    return n.compareTo(v) <= 0;
  }
}
