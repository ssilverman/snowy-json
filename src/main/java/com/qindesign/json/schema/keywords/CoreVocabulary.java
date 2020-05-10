/*
 * Created by shawn on 5/4/20 5:58 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements "$vocabulary".
 */
public class CoreVocabulary extends Keyword {
  public static final String NAME = "$vocabulary";

  public CoreVocabulary() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
      return true;
    }

    if (!context.isRootSchema()) {
      context.schemaError("appearance in subschema");
      return false;
    }
    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }

    Map<URI, Boolean> vocabularies = new HashMap<>();

    for (var e : value.getAsJsonObject().entrySet()) {
      URI uri;
      try {
        uri = new URI(e.getKey());
      } catch (URISyntaxException ex) {
        context.schemaError("not a valid URI", e.getKey());
        return false;
      }
      URI normalized = uri.normalize();
      if (!normalized.equals(uri)) {
        context.schemaError("URI not normalized", e.getKey());
        return false;
      }
      if (!Validator.isBoolean(e.getValue())) {
        context.schemaError("not a Boolean", e.getKey());
        return false;
      }

      // In theory, we shouldn't really set any vocabulary until all are known
      // to be okay
      vocabularies.put(uri, e.getValue().getAsBoolean());
    }

    for (var e : vocabularies.entrySet()) {
      context.setVocabulary(e.getKey(), e.getValue());
    }
    return true;
  }
}
