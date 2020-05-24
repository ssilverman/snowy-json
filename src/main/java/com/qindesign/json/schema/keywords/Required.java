/*
 * Created by shawn on 5/1/20 12:21 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "required" assertion.
 */
public class Required extends Keyword {
  public static final String NAME = "required";

  public Required() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonArray()) {
      context.schemaError("not an array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonObject()) {
      return true;
    }

    // Assume the number of properties is not unreasonable
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    JsonObject object = instance.getAsJsonObject();
    int index = 0;
    for (JsonElement e : value.getAsJsonArray()) {
      if (!Validator.isString(e)) {
        context.schemaError("not a string", Integer.toString(index));
        return false;
      }
      if (!object.has(e.getAsString())) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", \"");
        } else {
          sb.append("required properties not found: \"");
        }
        sb.append(Strings.jsonString(e.getAsString())).append('\"');
        context.setCollectSubAnnotations(false);
      }
      index++;
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
