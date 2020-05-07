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

    if (id.getRawFragment() != null) {
      if (!id.getRawFragment().isEmpty()) {
        context.schemaError("has a non-empty fragment");
        return false;
      }
      try {
        id = new URI(id.getScheme(), id.getRawSchemeSpecificPart(), null);
      } catch (URISyntaxException ex) {
        context.schemaError("unexpected bad URI");
        return false;
      }
    }

    context.setBaseURI(context.baseURI().resolve(id));
//    if (!context.addID(id)) {
//      context.schemaError("not unique");
//      return false;
//    }

    return true;
  }
}
