/*
 * Created by shawn on 5/1/20 12:16 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.util.Map;

/**
 * Implements the "additionalItems" applicator.
 */
public class AdditionalItems extends Keyword {
  public static final String NAME = "additionalItems";

  public AdditionalItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    JsonElement items = context.parentObject().get(Items.NAME);
    if (items == null || items.isJsonObject()) {
      return true;
    }

    if (!instance.isJsonArray()) {
      return true;
    }

    int processedCount = 0;

    Map<String, Annotation> annotations = context.getAnnotations(Items.NAME);
    Annotation a = annotations.get(context.schemaParentLocation() + "/" + Items.NAME);
    if (a != null) {
      if (a.value instanceof Boolean) {
        if ((Boolean) a.value) {
          return true;
        }
      } else if (a.value instanceof Integer) {
        processedCount = (Integer) a.value;
      }
    }

    boolean retval = true;

    JsonArray array = instance.getAsJsonArray();
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

    context.addAnnotation(NAME, true);

    return retval;
  }
}
