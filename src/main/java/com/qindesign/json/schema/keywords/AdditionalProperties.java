/*
 * Created by shawn on 5/1/20 3:34 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements the "additionalProperties" applicator.
 */
public class AdditionalProperties extends Keyword {
  public static final String NAME = "additionalProperties";

  public AdditionalProperties() {
    super(NAME);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!context.parentObject().has(Properties.NAME) &&
        !context.parentObject().has(PatternProperties.NAME)) {
      return true;
    }

    if (!instance.isJsonObject()) {
      return true;
    }

    Set<String> validated = new HashSet<>();

    // "properties"
    Map<URI, Annotation> annotations = context.getAnnotations(Properties.NAME);
    Annotation a = annotations.get(context.schemaParentLocation().resolve(Properties.NAME));
    if (a.value instanceof Set<?>) {
      validated.addAll((Set<String>) a.value);
    }

    // "patternProperties"
    annotations = context.getAnnotations(PatternProperties.NAME);
    a = annotations.get(context.schemaParentLocation().resolve(PatternProperties.NAME));
    if (a.value instanceof Set<?>) {
      validated.addAll((Set<String>) a.value);
    }

    JsonObject object = instance.getAsJsonObject();
    Set<String> thisValidated = new HashSet<>();
    if (validated.size() < object.size()) {
      for (var e : object.entrySet()) {
        if (validated.contains(e.getKey())) {
          continue;
        }
        if (!context.apply(value, "", e.getValue(), e.getKey())) {
          return false;
        }
        thisValidated.add(e.getKey());
      }
    }

    context.addAnnotation(NAME, thisValidated);

    return true;
  }
}
