/*
 * Created by shawn on 5/5/20 10:17 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements "$recursiveAnchor".
 */
public class CoreRecursiveAnchor extends Keyword {
  public static final String NAME = "$recursiveAnchor";

  public CoreRecursiveAnchor() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    if (!Validator.isBoolean(value)) {
      context.schemaError("not a boolean");
      return false;
    }

    if (value.getAsBoolean() && parent.has(CoreId.NAME)) {
      context.setRecursiveBaseURI();
    }
    return true;
  }
}
