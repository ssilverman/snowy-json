/*
 * Created by shawn on 5/1/20 3:18 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the "patternProperties" applicator.
 */
public class PatternProperties extends Keyword {
  public static final String NAME = "patternProperties";

  public PatternProperties() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
    }
    // Don't do all the schema validation here because it should have been
    // checked when validating the schema using the meta-schema

    if (!instance.isJsonObject()) {
      return true;
    }

    JsonObject schemaObject = value.getAsJsonObject();
    JsonObject object = instance.getAsJsonObject();

    // Compile all and check all the schema patterns
    List<java.util.regex.Pattern> patterns = new ArrayList<>(schemaObject.size());
    for (var e : schemaObject.entrySet()) {
      try {
        patterns.add(java.util.regex.Pattern.compile(e.getKey()));
      } catch (PatternSyntaxException ex) {
        // Technically, this is a "SHOULD" and not a "MUST"
        context.schemaError("not a valid pattern", e.getKey());
        return false;
      }
      context.checkValidSchema(e.getValue(), e.getKey());
    }

    Set<String> validated = new HashSet<>();
    for (var e : object.entrySet()) {
      // For each that matches, check the schema
      for (java.util.regex.Pattern p : patterns) {
        if (!p.matcher(e.getKey()).matches()) {
          continue;
        }
        if (!context.apply(schemaObject.getAsJsonObject(p.pattern()), p.pattern(),
                           e.getValue(), e.getKey())) {
          return false;
        }
        validated.add(e.getKey());
      }
    }

    context.addAnnotation(NAME, validated);

    return true;
  }
}
