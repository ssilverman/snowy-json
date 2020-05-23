/*
 * Created by shawn on 4/29/20 1:34 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Id;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Option;
import com.qindesign.json.schema.Options;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.URIs;
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
  public boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                       ValidatorContext context)
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

    if (!context.isRootSchema() && !parent.has(CoreId.NAME)) {
      context.schemaError("appearance in non-resource subschema");
      return false;
    }

    // Strip off any fragment
    // TODO: Warning?
    id = URIs.stripFragment(id);

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
      context.schemaError("malformed schema: " + id + ": " + ex.getMessage());
      return false;
    }
    Options opts2 = new Options();
    opts2.set(Option.FORMAT, false);
    opts2.set(Option.CONTENT, false);
    opts2.set(Option.COLLECT_ANNOTATIONS, false);
    opts2.set(Option.COLLECT_ERRORS, false);
    opts2.set(Option.DEFAULT_SPECIFICATION, spec);
    ValidatorContext context2 = new ValidatorContext(id, ids, context.knownURLs(), validated, opts2);
    if (!context2.apply(e, "", parent, "")) {
      context.schemaError("does not validate");
      return false;
    }

    // Add all the vocabularies if this isn't a meta-schema
    if (context.validatedSchemas().isEmpty()) {
      context2.vocabularies().forEach(context::setVocabulary);
    }

    context.setSpecification(spec);
    return true;
  }
}
