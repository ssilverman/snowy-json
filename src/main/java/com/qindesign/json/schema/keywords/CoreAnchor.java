/*
 * Created by shawn on 5/3/20 7:31 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;

/**
 * Implements "$anchor".
 */
public class CoreAnchor extends Keyword {
  public static final String NAME = "$anchor";

  /**
   * @see <a href="https://www.w3.org/TR/2006/REC-xml-names11-20060816/#NT-NCName">Namespaces in XML 1.1 (Second Edition): NCName</a>
   */
  private static final java.util.regex.Pattern PATTERN =
      java.util.regex.Pattern.compile("[A-Z_a-z][-A-Z_a-z.0-9]*");

  public CoreAnchor() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }
    if (!PATTERN.matcher(value.getAsString()).matches()) {
      context.schemaError("invalid plain name");
      return false;
    }

//    try {
//      if (!context.addAnchor(value.getAsString())) {
//        context.schemaError("not unique");
//        return false;
//      }
//    } catch (IllegalArgumentException ex) {
//      context.schemaError("not a valid anchor");
//      return false;
//    }

    return true;
  }
}
