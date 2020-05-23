/*
 * Created by shawn on 5/10/20 1:43 AM.
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
 * Implements "definitions".
 */
public class Definitions extends Keyword {
  public static final String NAME = "definitions";

  public Definitions() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

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
