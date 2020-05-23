/*
 * Created by shawn on 5/5/20 12:46 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Strings;
import com.qindesign.json.schema.URIs;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    URI uri;
    try {
      uri = new URI(value.getAsString()).normalize();
    } catch (URISyntaxException ex) {
      context.schemaError("not a valid URI");
      return false;
    }

    // Resolve and treat as either a JSON pointer or plain name
    // In both cases, this doesn't set the base URI of the first $id element
    // because CoreId will be doing this when encountered
    uri = context.baseURI().resolve(uri);
    JsonElement e;
    String fragment = uri.getRawFragment();
    if (fragment != null && Format.JSON_POINTER.matcher(fragment).matches()) {
      uri = URIs.stripFragment(uri);
      e = context.findAndSetRoot(uri);
      if (e != null) {
        e = context.followPointer(uri, e, Strings.fragmentToJSONPointer(fragment));
      }
    } else {
      // Plain name
      // TODO: Do I need to set the base to the closest ancestor's base?
      e = context.findAndSetRoot(uri);
      if (e != null) {
        // Since we're following to another schema, CoreId will set the new base
        // URI, so don't set it here
        if (e.isJsonObject() && !e.getAsJsonObject().has(CoreId.NAME)) {
          context.setBaseURI(uri);
        }
      }
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
