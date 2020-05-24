/*
 * Created by shawn on 5/5/20 10:10 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.URIs;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implements the "$recursiveRef" core applicator.
 */
public class CoreRecursiveRef extends Keyword {
  public static final String NAME = "$recursiveRef";

  public CoreRecursiveRef() {
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

    // Fragment
    if (!uri.isAbsolute()) {
      if (!uri.getRawSchemeSpecificPart().isEmpty()) {
        // Technically, this is a "MAY" and not a "MUST"
        context.schemaError("not a lone octothorpe");
        return false;
      }
    }
    if (URIs.hasNonEmptyFragment(uri)) {
      context.schemaError("has a non-empty fragment");
      return false;
    }

    // First, look for a $recursiveAnchor
    uri = URIs.stripFragment(uri);
    URI resolved = context.baseURI().resolve(uri);
    JsonElement e = context.findAndSetRoot(resolved);
    if (e == null) {
      context.schemaError("unknown reference: " + value.getAsString());
      return false;
    }
    if (!Validator.isSchema(e)) {
      context.schemaError("reference not a schema: " + resolved);
      return false;
    }
    if (e.isJsonObject() && e.getAsJsonObject().has(CoreRecursiveAnchor.NAME)) {
      JsonElement a = e.getAsJsonObject().get(CoreRecursiveAnchor.NAME);
      if (!Validator.isBoolean(a)) {
        context.schemaError("referenced " + CoreRecursiveAnchor.NAME + " not a Boolean: " +
                            resolved);
        return false;
      }

      // Process the dynamic reference
      if (a.getAsBoolean()) {
        // Assume the recursive anchors have already been processed
        // Resolve against the new URI
        resolved = context.recursiveBaseURI().resolve(uri);
        e = context.findAndSetRoot(resolved);
        if (e == null) {
          context.schemaError("unknown dynamic reference: " + resolved);
          return false;
        }
        if (!Validator.isSchema(e)) {
          context.schemaError("dynamic reference not a schema: " + resolved);
          return false;
        }
      }
    }

    return context.apply(e, "", uri, instance, "");
  }
}
