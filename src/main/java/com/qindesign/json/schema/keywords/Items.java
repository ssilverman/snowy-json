/*
 * Created by shawn on 5/1/20 1:13 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "items" applicator.
 */
public class Items extends Keyword {
  public static final String NAME = "items";

  public Items() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isSchema(value) && !value.isJsonArray()) {
      context.schemaError("not a schema or array");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonArray()) {
      return true;
    }

    JsonArray array = instance.getAsJsonArray();

    // Should we trust user input not being too huge and collect all the
    // invalid indexes?
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    if (value.isJsonArray()) {
      JsonArray schemaArray = value.getAsJsonArray();
      int limit = Math.min(schemaArray.size(), array.size());
      for (int i = 0; i < limit; i++) {
        String path = Integer.toString(i);
        if (!context.apply(schemaArray.get(i), path, null, array.get(i), path)) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", ");
          } else {
            sb.append("invalid items in array: ");
          }
          sb.append(i);
          context.setCollectSubAnnotations(false);
        }
      }
      if (sb.length() == 0) {
        // TODO: Produce an annotation if items is empty?
        if (0 < limit && limit < array.size()) {
          context.addAnnotation(NAME, limit - 1);
        } else {
          context.addAnnotation(NAME, true);
        }
      }
    } else {
      int index = 0;
      for (JsonElement e : array) {
        if (!context.apply(value, "", null, e, Integer.toString(index))) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", ");
          } else {
            sb.append("invalid items: ");
          }
          sb.append(index);
          context.setCollectSubAnnotations(false);
        }
        index++;
      }
      if (sb.length() == 0) {
        context.addAnnotation(NAME, true);
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }
    return true;
  }
}
