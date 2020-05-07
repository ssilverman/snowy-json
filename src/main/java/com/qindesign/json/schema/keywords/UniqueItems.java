/*
 * Created by shawn on 4/30/20 8:08 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements the "uniqueItems" assertion.
 */
public class UniqueItems extends Keyword {
  public static final String NAME = "uniqueItems";

  public UniqueItems() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!instance.isJsonArray()) {
      return true;
    }
    Set<JsonElement> set = new HashSet<>();
    for (JsonElement e : instance.getAsJsonArray()) {
      if (!set.add(e)) {
        return false;
      }
    }
    return true;
  }
}
