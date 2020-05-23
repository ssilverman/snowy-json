/*
 * Created by shawn on 5/1/20 12:16 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "additionalItems" applicator.
 */
public class AdditionalItems extends Keyword {
  public static final String NAME = "additionalItems";

  public AdditionalItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    JsonElement items = parent.get(Items.NAME);
    if (items == null || items.isJsonObject()) {
      return true;
    }
    if (!items.isJsonArray()) {
      // Let the Items keyword validate
      return true;
    }

    if (!instance.isJsonArray()) {
      return true;
    }

    JsonArray schemaArray = items.getAsJsonArray();
    JsonArray array = instance.getAsJsonArray();

    int processedCount = Math.min(schemaArray.size(), array.size());
    boolean retval = true;

    for (int i = processedCount; i < array.size(); i++) {
      if (!context.apply(value, "", array.get(i), Integer.toString(i))) {
        if (context.isFailFast()) {
          return false;
        }
        context.addError(false, "additional item " + i + " not valid");
        retval = false;
        context.setCollectSubAnnotations(false);
      }
    }

    if (retval) {
      // Don't produce annotations if this keyword is not applied
      // TODO: Verify this restriction
      if (processedCount < array.size()) {
        context.addAnnotation(NAME, true);
      }
    }
    return retval;
  }
}
