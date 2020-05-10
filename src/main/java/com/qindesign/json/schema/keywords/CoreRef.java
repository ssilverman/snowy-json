/*
 * Created by shawn on 5/5/20 12:46 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implements the "$ref" core applicator.
 */
public class CoreRef extends Keyword {
  public static final String NAME = "$ref";

  public CoreRef() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      if (context.parentObject().size() != 1) {
        context.schemaError("has siblings");
        return false;
      }
    }

    URI uri;
    try {
      uri = new URI(value.getAsString()).normalize();
    } catch (URISyntaxException ex) {
      context.schemaError("not a valid URI");
      return false;
    }

    // Resolve and treat as either a JSON pointer or plain name
    uri = context.baseURI().resolve(uri);
    JsonElement e;
    String fragment = uri.getRawFragment();
    if (fragment != null && Format.JSON_POINTER.matcher(fragment).matches()) {
      try {
        e = context.findAndSetRoot(new URI(uri.getScheme(), uri.getRawSchemeSpecificPart(), null));
      } catch (URISyntaxException ex) {
        context.schemaError("unexpected bad URI");
        return false;
      }
      if (e != null) {
        e = Validator.followPointer(e, Strings.fragmentToJSONPointer(fragment));
      }
    } else {
      // Plain name
      e = context.findAndSetRoot(uri);
    }

    if (e == null) {
      context.schemaError("unknown reference: " + value.getAsString());
      return false;
    }
    if (!Validator.isSchema(e)) {
      context.schemaError("reference not a schema: " + value.getAsString());
      return false;
    }

    return context.apply(e, "", instance, "");
  }
}
