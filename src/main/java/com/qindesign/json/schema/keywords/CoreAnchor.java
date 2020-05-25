/*
 * Created by shawn on 5/3/20 7:31 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements "$anchor".
 */
public class CoreAnchor extends Keyword {
  public static final String NAME = "$anchor";

  public CoreAnchor() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    if (!JSON.isString(value)) {
      context.schemaError("not a string");
      return false;
    }
    if (!Validator.ANCHOR_PATTERN.matcher(value.getAsString()).matches()) {
      context.schemaError("invalid plain name");
      return false;
    }

    return true;
  }
}
