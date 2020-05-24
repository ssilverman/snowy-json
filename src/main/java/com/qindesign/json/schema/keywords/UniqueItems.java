/*
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements the "uniqueItems" assertion.
 */
public class UniqueItems extends Keyword {
  public static final String NAME = "uniqueItems";

  public UniqueItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isBoolean(value)) {
      context.schemaError("not a Boolean");
      return false;
    }

    if (!value.getAsBoolean()) {
      return true;
    }

    if (!instance.isJsonArray()) {
      return true;
    }

    // Assume the number of items is not unreasonable
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    Set<JsonElement> set = new HashSet<>();
    int index = 0;
    for (JsonElement e : instance.getAsJsonArray()) {
      if (!set.add(e)) {
        if (context.isFailFast()) {
          return false;
        }
        if (sb.length() > 0) {
          sb.append(", ");
        } else {
          sb.append("non-unique items: ");
        }
        sb.append(index);
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
