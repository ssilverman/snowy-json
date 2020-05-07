/*
 * Created by shawn on 5/3/20 7:31 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
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
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
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
