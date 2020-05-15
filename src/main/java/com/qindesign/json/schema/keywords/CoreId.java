/*
 * Created by shawn on 4/29/20 12:44 AM.
 */
package com.qindesign.json.schema.keywords;

import static com.qindesign.json.schema.Validator.ANCHOR_PATTERN;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.URIs;
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

    if (URIs.hasNonEmptyFragment(id)) {
      if (context.specification().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
        context.schemaError("has a non-empty fragment");
        return false;
      }
      if (!ANCHOR_PATTERN.matcher(id.getRawFragment()).matches()) {
        context.schemaError("invalid plain name");
        return false;
      }

      // If it's not just a fragment then it represents a new base URI
      if (id.getScheme() != null || !id.getRawSchemeSpecificPart().isEmpty()) {
        context.setBaseURI(id);
      }
    } else {
      id = URIs.stripFragment(id);
      context.setBaseURI(id);
    }

    return true;
  }
}
