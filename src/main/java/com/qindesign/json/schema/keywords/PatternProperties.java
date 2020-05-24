/*
 * Created by shawn on 5/1/20 3:18 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
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

    // Compile all and check all the schema patterns
    List<java.util.regex.Pattern> patterns = new ArrayList<>(schemaObject.size());
    for (var e : schemaObject.entrySet()) {
      try {
        patterns.add(context.patternCache().access(e.getKey()));
      } catch (PatternSyntaxException ex) {
        // Technically, this is a "SHOULD" and not a "MUST"
        context.schemaError("not a valid pattern", e.getKey());
        return false;
      }
      context.checkValidSchema(e.getValue(), e.getKey());
    }

    // Assume the number of properties is not unreasonable
    StringBuilder sb = new StringBuilder();

    Set<String> validated = new HashSet<>();
    for (var e : object.entrySet()) {
      // For each that matches, check the schema
      for (java.util.regex.Pattern p : patterns) {
        if (!p.matcher(e.getKey()).find()) {
          continue;
        }
        if (!context.apply(schemaObject.get(p.pattern()), p.pattern(), null,
                           e.getValue(), e.getKey())) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", \"");
          } else {
            sb.append("invalid properties: \"");
          }
          sb.append(Strings.jsonString(e.getKey())).append("\" matches \"")
              .append(Strings.jsonString(p.pattern())).append('\"');
          context.setCollectSubAnnotations(false);
        }
        validated.add(e.getKey());
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }

    context.addAnnotation(NAME, validated);
    context.addLocalAnnotation(NAME, validated);
    return true;
  }
}
