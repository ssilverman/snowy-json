/*
 * Created by shawn on 5/2/20 10:45 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements the "if"/"then"/"else" applicators.
 */
public class If extends Keyword {
  public static final String NAME = "if";

  public If() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    context.checkValidSchema(value);

    JsonObject parent = context.parentObject();
    JsonElement thenElem = parent.get("then");
    if (thenElem != null) {
      context.checkValidSchema(thenElem, "../then");
    }
    JsonElement elseElem = parent.get("else");
    if (elseElem != null) {
      context.checkValidSchema(elseElem, "../else");
    }

    if (context.apply(value, "", instance, "")) {
      if (thenElem == null) {
        return true;
      }
      return context.apply(thenElem, "../then", instance, "");
    } else {
      if (elseElem == null) {
        return true;
      }
      return context.apply(elseElem, "../else", instance, "");
    }
  }
}
