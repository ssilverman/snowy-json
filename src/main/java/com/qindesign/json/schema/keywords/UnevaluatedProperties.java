/*
 * Created by shawn on 5/3/20 7:26 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implements the "unevaluatedProperties" applicator.
 */
public class UnevaluatedProperties extends Keyword {
  public static final String NAME = "unevaluatedProperties";

  public UnevaluatedProperties() {
    super(NAME);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!context.parentObject().has(Properties.NAME)) {
      return true;
    }

    if (!instance.isJsonObject()) {
      return true;
    }
    JsonObject object = instance.getAsJsonObject();

    String loc = context.schemaParentLocation();
    Set<String> validated = new HashSet<>();

    Consumer<Map<String, Annotation>> f = (Map<String, Annotation> a) -> {
      if (validated.size() >= object.size()) {
        return;
      }
      for (var e : a.entrySet()) {
        if (!e.getKey().startsWith(loc)) {
          continue;
        }
        if (e.getValue().value instanceof Set<?>) {
          validated.addAll((Set<String>) e.getValue().value);
        }
      }
    };

    f.accept(context.getAnnotations(Properties.NAME));
    f.accept(context.getAnnotations(PatternProperties.NAME));
    f.accept(context.getAnnotations(AdditionalProperties.NAME));
    f.accept(context.getAnnotations(NAME));

    Set<String> thisValidated = new HashSet<>();
    if (validated.size() < object.size()) {
      for (var e : object.entrySet()) {
        if (validated.contains(e.getKey())) {
          continue;
        }
        if (!context.apply(e.getValue(), "", e.getValue(), e.getKey())) {
          return false;
        }
        thisValidated.add(e.getKey());
      }
    }

    context.addAnnotation(NAME, thisValidated);

    return true;
  }
}
