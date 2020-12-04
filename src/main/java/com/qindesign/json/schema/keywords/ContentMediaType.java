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
 * Created by shawn on 5/9/20 11:29 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Option;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.ValidatorContext;
import com.qindesign.json.schema.util.Base64InputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * Implements the "contentMediaType" annotation.
 */
public class ContentMediaType extends Keyword {
  public static final String NAME = "contentMediaType";

  // https://www.rfc-editor.org/rfc/rfc2045.html
  private static final String TOKEN = "[!#$%&'*+-.0-9A-Z^_`a-z{|}~]+";
  private static final java.util.regex.Pattern CONTENT_TYPE =
      java.util.regex.Pattern.compile(
          "^(?<mediaType>" + TOKEN + "/" + TOKEN + ")" +
          "(?:\\s*;\\s*" + TOKEN + "=(?:" + TOKEN + "|\"(?:[ -~&&[^\"]]|\\\\[\\x00-\\x7f])*\"))*\\s*$");

  public ContentMediaType() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                          ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().compareTo(Specification.DRAFT_07) < 0) {
      return true;
    }

    if (!JSON.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    // TODO: Collect annotation anyway?
    if (!JSON.isString(instance)) {
      return true;
    }

    context.addAnnotation(NAME, value.getAsString());

    // Only Draft-07 can make this a validation assertion
    if (context.specification() == Specification.DRAFT_07) {
      if (context.isOption(Option.CONTENT)) {
        // Next look at the media type
        Matcher m = CONTENT_TYPE.matcher(value.getAsString());
        if (m.matches()) {
          String contentType = m.group("mediaType");
          if (contentType.equalsIgnoreCase("application/json")) {
            // Determine if Base64-encoded
            boolean base64 = false;
            JsonElement encoding = parent.get(ContentEncoding.NAME);
            if (encoding != null && JSON.isString(encoding)) {
              if (encoding.getAsString().equalsIgnoreCase("base64")) {
                base64 = true;
              }
            }

            Reader content;
            if (base64) {
              content = new InputStreamReader(new Base64InputStream(instance.getAsString()),
                                              StandardCharsets.UTF_8);
            } else {
              content = new StringReader(instance.getAsString());
            }

            try (Reader r = content) {
              JSON.parse(r);
            } catch (JsonParseException ex) {
              if (ex.getCause() instanceof IOException) {
                context.addError(false, "bad base64 encoding");
              } else {
                context.addError(false, "does not validate against application/json");
              }
              return false;
            } catch (IOException ex) {
              return false;
            }
          }
        } else {
          context.addError(false, "invalid media type: " + value.getAsString());
          return false;
        }
      }
    }

    return true;
  }
}
