/*
 * Created by shawn on 4/29/20 12:44 AM.
 */
package com.qindesign.json.schema.keywords;

import static com.qindesign.json.schema.Validator.ANCHOR_PATTERN;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;

/**
 * Implements "$id".
 */
public class CoreId extends Keyword {
  public static final String NAME = "$id";

  public CoreId() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    URI id = context.getID(value, "");
    if (id != null) {
      context.setBaseURI(id);
    }
    return true;
  }
}
