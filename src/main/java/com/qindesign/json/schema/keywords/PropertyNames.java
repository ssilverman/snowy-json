/*
 * Created by shawn on 5/2/20 3:28 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "propertyNames" applicator.
 */
public class PropertyNames extends Keyword {
  public static final String NAME = "propertyNames";

  public PropertyNames() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonObject()) {
      return true;
    }

    for (String name : instance.getAsJsonObject().keySet()) {
      if (!context.apply(value, "", new JsonPrimitive(name), name)) {
        context.addError(false, "property name \"" + Strings.jsonString(name) + "\" not valid");
        return false;
      }
    }
    return true;
  }
}
