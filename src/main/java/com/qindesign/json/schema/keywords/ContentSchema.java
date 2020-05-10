/*
 * Created by shawn on 5/10/20 12:31 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "contentSchema" annotation.
 */
public class ContentSchema extends Keyword {
  public static final String NAME = "contentSchema";

  public ContentSchema() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(instance)) {
      return true;
    }

    if (!context.parentObject().has(ContentMediaType.NAME)) {
      return true;
    }

    if (!Validator.isSchema(value)) {
      context.schemaError("not a schema");
      return false;
    }

    context.addAnnotation(NAME, value);
    return true;
  }
}
