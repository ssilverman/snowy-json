/*
 * Created by shawn on 5/5/20 10:43 PM.
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
 * Implements the "readOnly" annotation.
 */
public class ReadOnly extends Keyword {
  public static final String NAME = "readOnly";

  public ReadOnly() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_07.ordinal()) {
      return true;
    }

    if (!Validator.isBoolean(value)) {
      context.schemaError("not a Boolean");
      return false;
    }

    context.addAnnotation(NAME, value.getAsBoolean());
    return true;
  }
}
