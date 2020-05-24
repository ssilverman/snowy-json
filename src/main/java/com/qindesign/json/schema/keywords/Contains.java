/*
 * Created by shawn on 5/1/20 12:01 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Numbers;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.math.BigDecimal;

/**
 * Implements the "contains" applicator.
 */
public class Contains extends Keyword {
  public static final String NAME = "contains";

  public Contains() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonArray()) {
      return true;
    }

    int validCount = 0;
    int index = 0;

    // Apply all of them to collect all annotations
    for (JsonElement e : instance.getAsJsonArray()) {
      if (context.apply(value, "", null, e, Integer.toString(index++))) {
        validCount++;
      }
    }

    // Special handling if there's a minContains == 0
    boolean allowZero = false;
    if (context.specification().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
      JsonElement minContains = parent.get(MinContains.NAME);
      if (minContains != null && Validator.isNumber(minContains)) {
        BigDecimal n = Numbers.valueOf(minContains.getAsString());
        if (n.signum() == 0) {
          allowZero = true;
        }
      }
    }

    if (allowZero || validCount > 0) {
      context.addAnnotation(NAME, validCount);
      context.addLocalAnnotation(NAME, validCount);
      return true;
    }
    return false;
  }
}
