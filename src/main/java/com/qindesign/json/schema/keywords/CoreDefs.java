/*
 * Created by shawn on 5/5/20 10:20 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements "$defs".
 */
public class CoreDefs extends Keyword {
  public static final String NAME = "$defs";

  public CoreDefs() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }

    for (var e : value.getAsJsonObject().entrySet()) {
      if (!Validator.isSchema(e.getValue())) {
        context.schemaError("not a schema", e.getKey());
        return false;
      }
    }

    return true;
  }
}
