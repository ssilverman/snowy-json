/*
 * Created by shawn on 5/10/20 12:31 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    context.checkValidSchema(value);

    if (!JSON.isString(instance)) {
      return true;
    }

    if (!parent.has(ContentMediaType.NAME)) {
      return true;
    }

    context.addAnnotation(NAME, value);
    return true;
  }
}
