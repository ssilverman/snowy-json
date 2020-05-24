/*
 * Created by shawn on 5/2/20 3:28 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonObject()) {
      return true;
    }

    // Assume the number of properties is not unreasonable
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    for (String name : instance.getAsJsonObject().keySet()) {
      if (!context.apply(value, "", null, new JsonPrimitive(name), name)) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", \"");
        } else {
          sb.append("invalid property names: \"");
        }
        sb.append(Strings.jsonString(name)).append('\"');
        context.setCollectSubAnnotations(false);
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
