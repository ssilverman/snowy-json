/*
 * Created by shawn on 5/9/20 11:29 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "contentEncoding" annotation.
 */
public class ContentEncoding extends Keyword {
  public static final String NAME = "contentEncoding";

  public ContentEncoding() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_07.ordinal()) {
      return true;
    }

    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    if (!Validator.isString(instance)) {
      return true;
    }

    context.addAnnotation(NAME, value.getAsString());
    return true;
  }
}
