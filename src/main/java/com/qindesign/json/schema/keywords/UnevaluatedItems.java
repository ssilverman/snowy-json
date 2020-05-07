/*
 * Created by shawn on 5/3/20 7:24 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.util.Map;
import java.util.function.Function;

/**
 * Implements the "unevaluatedItems" applicator.
 */
public class UnevaluatedItems extends Keyword {
  public static final String NAME = "unevaluatedItems";

  public UnevaluatedItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!context.parentObject().has(Items.NAME)) {
      return true;
    }

    if (!instance.isJsonArray()) {
      return true;
    }

    String loc = context.schemaParentLocation().toString();
    int max = 0;

    // Returns true if we need to return true and false to not return
    Function<Map<URI, Annotation>, Boolean> f = (Map<URI, Annotation> a) -> {
      for (var e : a.entrySet()) {
        if (!e.getKey().toString().startsWith(loc)) {
          continue;
        }
        Object v = e.getValue().value;
        if (v == null) {
          continue;
        }
        if (v.equals(true)) {
          return true;
        }
      }
      return false;
    };

    if (f.apply(context.getAnnotations(AdditionalItems.NAME))) {
      return true;
    }
    if (f.apply(context.getAnnotations(UnevaluatedItems.NAME))) {
      return true;
    }

    // "items"
    Map<URI, Annotation> annotations = context.getAnnotations(Items.NAME);
    for (var e : annotations.entrySet()) {
      if (!e.getKey().toString().startsWith(loc)) {
        continue;
      }
      Object v = e.getValue().value;
      if (v == null) {
        continue;
      }
      if (v.equals(true)) {
        return true;
      }
      if (v instanceof Integer) {
        max = Math.max(max, (Integer) v);
      }
    }

    JsonArray array = instance.getAsJsonArray();
    for (int i = max; i < array.size(); i++) {
      if (context.apply(value, "", array.get(i), Integer.toString(i))) {
        return false;
      }
    }
    context.addAnnotation(NAME, true);
    return true;
  }
}
