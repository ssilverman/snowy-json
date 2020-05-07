/*
 * Created by shawn on 5/5/20 10:42 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "default" annotation.
 */
public class Default extends Keyword {
  public static final String NAME = "default";

  public Default() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.addAnnotation(NAME, value);
    return true;
  }
}
