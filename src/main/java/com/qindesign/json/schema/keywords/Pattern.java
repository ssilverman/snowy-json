/*
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the "pattern" assertion.
 */
public class Pattern extends Keyword {
  public static final String NAME = "pattern";

  public Pattern() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    java.util.regex.Pattern p;
    try {
      p = context.patternCache().access(value.getAsString());
    } catch (PatternSyntaxException ex) {
      // Technically, this is a "SHOULD" and not a "MUST"
      context.schemaError("not a valid pattern");
      return false;
    }

    if (!Validator.isString(instance)) {
      return true;
    }

    if (!p.matcher(instance.getAsString()).find()) {
      context.addError(
          false,
          "string '" + Strings.jsonString(instance.getAsString()) +
          "' does not match pattern '" + Strings.jsonString(value.getAsString()) + "'");
      return false;
    }
    return true;
  }
}
