/*
 * Created by shawn on 4/29/20 12:44 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implements "$id".
 */
public class CoreId extends Keyword {
  public static final String NAME = "$id";

  public CoreId() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    URI id;
    try {
      id = new URI(value.getAsString()).normalize();
    } catch (URISyntaxException ex) {
      context.schemaError("not a valid URI-reference");
      return false;
    }

    if (Validator.hasNonEmptyFragment(id)) {
      context.schemaError("has a non-empty fragment");
      return false;
    }
    id = Validator.stripFragment(id);

    context.setBaseURI(id);

    return true;
  }
}
