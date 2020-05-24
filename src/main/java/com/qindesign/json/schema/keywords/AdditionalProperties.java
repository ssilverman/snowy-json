/*
 * Created by shawn on 5/1/20 3:34 PM.
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
 * Implements the "additionalProperties" applicator.
 */
public class AdditionalProperties extends Keyword {
  public static final String NAME = "additionalProperties";

  public AdditionalProperties() {
    super(NAME);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    if (!instance.isJsonObject()) {
      return true;
    }

    Set<String> validated = new HashSet<>();

    // "properties"
    Object propsA = context.localAnnotation(Properties.NAME);
    if (propsA instanceof Set<?>) {
      validated.addAll((Set<String>) propsA);
    }

    // "patternProperties"
    propsA = context.localAnnotation(PatternProperties.NAME);
    if (propsA instanceof Set<?>) {
      validated.addAll((Set<String>) propsA);
    }

    // Let's assume that there aren't an unreasonable number of additional
    // properties in the user input, so track the bad ones
    // TODO: What should we do here, count or collect?
    StringBuilder sb = new StringBuilder();

    JsonObject object = instance.getAsJsonObject();
    Set<String> thisValidated = new HashSet<>();
    if (validated.size() < object.size()) {
      for (var e : object.entrySet()) {
        if (validated.contains(e.getKey())) {
          continue;
        }
        if (!context.apply(value, "", null, e.getValue(), e.getKey())) {
          if (context.isFailFast()) {
            return false;
          }
          if (sb.length() > 0) {
            sb.append(", \"");
          } else {
            sb.append("invalid additional properties: \"");
          }
          sb.append(Strings.jsonString(e.getKey())).append('\"');
          context.setCollectSubAnnotations(false);
        }
        thisValidated.add(e.getKey());
      }
    }

    if (sb.length() > 0) {
      context.addError(false, sb.toString());
      return false;
    }

    // There's a good chance that no annotation should be collected if the
    // keyword is not applied
    // TODO: Verify this
    if (thisValidated.size() > 0) {
      context.addAnnotation(NAME, thisValidated);
    }
    return true;
  }
}
