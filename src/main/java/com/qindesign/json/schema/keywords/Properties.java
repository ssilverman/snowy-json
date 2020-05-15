/*
 * Created by shawn on 5/1/20 2:17 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements the "properties" applicator.
 */
public class Properties extends Keyword {
  public static final String NAME = "properties";

  public Properties() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonObject()) {
      return true;
    }

    JsonObject schemaObject = value.getAsJsonObject();
    JsonObject object = instance.getAsJsonObject();

    boolean retval = true;

    Set<String> validated = new HashSet<>();
    for (var e : object.entrySet()) {
      if (!schemaObject.has(e.getKey())) {
        continue;
      }
      if (!context.apply(schemaObject.get(e.getKey()), e.getKey(), e.getValue(), e.getKey())) {
        if (context.isFailFast()) {
          return false;
        }
        context.addError(false, "property \"" + Strings.jsonString(e.getKey()) + "\" not valid");
        retval = false;
        context.setCollectSubAnnotations(false);
      }
      validated.add(e.getKey());
    }

    context.addAnnotation(NAME, validated);

    return retval;
  }
}
