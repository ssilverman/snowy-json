/*
 * Created by shawn on 5/3/20 11:40 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Annotation;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Numbers;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

/**
 * Implements the "minContains" assertion.
 */
public class MinContains extends Keyword {
  public static final String NAME = "minContains";

  public MinContains() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isNumber(value)) {
      context.schemaError("not a number");
      return false;
    }
    BigDecimal n = Numbers.valueOf(value.getAsString());
    if (n.compareTo(BigDecimal.ZERO) < 0) {
      context.schemaError("not >= 0");
      return false;
    }

    if (!context.parentObject().has(Contains.NAME)) {
      return true;
    }

    Map<URI, Annotation> contains = context.getAnnotations(Contains.NAME);
    Annotation a = contains.get(context.schemaParentLocation().resolve(Contains.NAME));
    if (a == null) {
      return true;
    }
    return n.compareTo(BigDecimal.valueOf(((Integer) a.value).longValue())) <= 0;
//    Integer v = (Integer) context.props().get("contains");
//    if (v == null) {
//      return true;
//    }
//    return n.compareTo(BigDecimal.valueOf(v.longValue())) <= 0;
  }
}
