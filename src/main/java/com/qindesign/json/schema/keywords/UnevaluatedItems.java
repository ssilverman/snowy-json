/*
 * Created by shawn on 5/3/20 7:24 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;
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
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }
    if (!context.isCollectAnnotations()) {
      context.schemaError("annotations are not being collected");
      return false;
    }

    context.checkValidSchema(value);

    if (!instance.isJsonArray()) {
      return true;
    }

    String loc = context.schemaParentLocation();
    int max = 0;

    // Returns true if we need to return true and false to not return
    Function<Map<String, Annotation>, Boolean> f = (Map<String, Annotation> a) -> {
      for (var e : a.entrySet()) {
        if (!e.getKey().startsWith(loc)) {
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

    if (f.apply(context.annotations(AdditionalItems.NAME))) {
      return true;
    }
    if (f.apply(context.annotations(UnevaluatedItems.NAME))) {
      return true;
    }

    // "items"
    Map<String, Annotation> annotations = context.annotations(Items.NAME);
    for (var e : annotations.entrySet()) {
      if (!e.getKey().startsWith(loc)) {
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

    boolean retval = true;

    JsonArray array = instance.getAsJsonArray();
    for (int i = max; i < array.size(); i++) {
      if (!context.apply(value, "", array.get(i), Integer.toString(i))) {
        if (context.isFailFast()) {
          return false;
        }
        context.addError(false, "unevaluated item " + i + " not valid");
        retval = false;
        context.setCollectSubAnnotations(false);
      }
    }

    if (retval) {
      context.addAnnotation(NAME, true);
    }
    return retval;
  }
}
