/*
 * Created by shawn on 4/29/20 1:34 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.qindesign.json.schema.Id;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements "$schema".
 */
public class CoreSchema extends Keyword {
  public static final String NAME = "$schema";

  public CoreSchema() {
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
      id = new URI(value.getAsString());
    } catch (URISyntaxException ex) {
      context.schemaError("not a URI");
      return false;
    }

    if (!id.isAbsolute()) {
      context.schemaError("missing scheme");
      return false;
    }

    URI normalized = id.normalize();
    if (!normalized.equals(id)) {
      context.schemaError("not normalized");
      return false;
    }

    if (!context.isRootSchema() && !context.parentObject().has(CoreId.NAME)) {
      context.schemaError("appearance in non-resource subschema");
      return false;
    }

    // Strip off any fragment
    id = Validator.stripFragment(id);

    // Check if we should validate the schema
    Set<URI> validated = context.validatedSchemas();
    if (validated.contains(id)) {
      return true;
    }

    Specification spec = Specification.of(id);
    if (spec == null) {
      context.schemaError("unknown schema ID");
      return false;
    }

    JsonElement e = Validator.loadResource(id);
    if (e == null) {
      context.schemaError("unknown schema resource");
      return false;
    }

    validated = new HashSet<>(validated);
    validated.add(id);

    Map<Id, JsonElement> ids;
    try {
      ids = Validator.scanIDs(id, e, spec);
    } catch (MalformedSchemaException ex) {
      context.schemaError("malformed schema: " + ex.getMessage());
      return false;
    }
    ValidatorContext context2 = new ValidatorContext(id, ids, validated);
    if (!context2.apply(e, "", context.parentObject(), "")) {
      context.schemaError("does not validate");
      return false;
    }

    context.setSpecification(spec);
    return true;
  }
}
