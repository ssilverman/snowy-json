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
 * Created by shawn on 5/5/20 12:46 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qindesign.json.schema.Id;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.URIs;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;

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
    if (!JSON.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    URI uri;
    try {
      uri = URI.parse(value.getAsString()).normalize();
    } catch (URISyntaxException ex) {
      context.schemaError("not a valid URI");
      return false;
    }

    // Resolve and treat as either a JSON Pointer or plain name
    // In both cases, this doesn't set the base URI of the first $id element
    // because CoreId will be doing this when encountered
    uri = context.baseURI().resolve(uri);
    URI schemaURI = uri;
    JsonElement e;
    String fragment = uri.fragment();
    if (fragment != null && Format.JSON_POINTER.matcher(fragment).matches()) {
      uri = URIs.stripFragment(uri);
      e = context.findAndSetRoot(uri);
      if (e != null) {
        e = context.followPointer(uri, e, fragment);
      }
    } else {
      // No fragment or plain name

      // DONETODO: Do I need to set the base to the closest ancestor's base?
      // I think the answer is yes because it's possible that canonical URIs
      // don't have plain names
      if (fragment != null) {
        Id id = context.findID(uri);
        if (id != null) {  // May be no anchor in earlier specs
          try {
            schemaURI =
                new URI(id.base.scheme(), id.base.authority(),
                        id.base.path(),
                        id.base.query(), id.path.toString());
          } catch (URISyntaxException ex) {
            context.schemaError("unexpected bad URI");
            return false;
          }
        }
      }

      e = context.findAndSetRoot(uri);
    }

    if (e == null) {
      context.schemaError("unknown reference: " + value.getAsString());
      return false;
    }
    if (!Validator.isSchema(e)) {
      context.schemaError("reference not a schema: " + value.getAsString());
      return false;
    }

    return context.apply(e, null, schemaURI, instance, null);
  }
}
