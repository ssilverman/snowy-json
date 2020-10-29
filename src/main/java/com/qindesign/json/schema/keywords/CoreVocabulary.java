/*
 * Snow, a JSON Schema validator
 * Copyright (c) 2020  Shawn Silverman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Created by shawn on 5/4/20 5:58 PM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
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
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().compareTo(Specification.DRAFT_2019_09) < 0) {
      return true;
    }

    if (!value.isJsonObject()) {
      context.schemaError("not an object");
      return false;
    }

    // Ignore if not a meta-schema
    if (!context.isMetaSchema()) {
      return true;
    }
    if (!context.isRootSchema()) {
      context.schemaError("appearance in subschema");
      return false;
    }

    Map<URI, Boolean> vocabularies = new HashMap<>();

    for (var e : value.getAsJsonObject().entrySet()) {
      URI uri;
      try {
        uri = URI.parse(e.getKey());
      } catch (URISyntaxException ex) {
        context.schemaError("not a valid URI", e.getKey());
        return false;
      }
      URI normalized = uri.normalize();
      if (!normalized.equals(uri)) {
        context.schemaError("URI not normalized", e.getKey());
        return false;
      }
      if (!JSON.isBoolean(e.getValue())) {
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
