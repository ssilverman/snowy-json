/*
 * Created by shawn on 5/5/20 10:17 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
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
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isBoolean(value)) {
      context.schemaError("not a boolean");
      return false;
    }

    if (value.getAsBoolean() && context.parentObject().has(CoreId.NAME)) {
      context.setRecursiveBaseURI();
    }
    return true;
  }
}
