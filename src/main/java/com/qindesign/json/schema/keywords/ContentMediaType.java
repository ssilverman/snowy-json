/*
 * Created by shawn on 5/9/20 11:29 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.JSON;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Option;
import com.qindesign.json.schema.Specification;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;

/**
 * Implements the "contentMediaType" annotation.
 */
public class ContentMediaType extends Keyword {
  public static final String NAME = "contentMediaType";

  private static final String TOKEN = "[!#$%&'*+-.0-9A-Z^_`a-z{|}~]+";
  private static final java.util.regex.Pattern CONTENT_TYPE =
      java.util.regex.Pattern.compile(
          "^(?<mediaType>" + TOKEN + "/" + TOKEN + ")" +
          "(?:\\s*;\\s*" + TOKEN + "=(?:" + TOKEN + "|\"(?:[ -~&&[^\"]]|\\\\[\\x00-\\x7f])*\"))*\\s*$");

  public ContentMediaType() {
    super(NAME);
  }

  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (context.specification().ordinal() < Specification.DRAFT_07.ordinal()) {
      return true;
    }

    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    if (!Validator.isString(instance)) {
      return true;
    }

    if (context.isOption(Option.CONTENT)) {
      // First look at the encoding
      byte[] b = null;
      JsonElement encoding = context.parentObject().get(ContentEncoding.NAME);
      if (encoding != null && Validator.isString(encoding)) {
        if (encoding.getAsString().equalsIgnoreCase("base64")) {
          try {
            b = Base64.getDecoder().decode(instance.getAsString());
          } catch (IllegalArgumentException ex) {
            context.addError(false, "bad base64 encoding");
            return false;
          }
        }
      }

      // Next look at the media type
      Matcher m = CONTENT_TYPE.matcher(value.getAsString());
      if (m.matches()) {
        String contentType = m.group("mediaType");
        if (contentType.equalsIgnoreCase("application/json")) {
          Reader content;
          if (b == null) {
            content = new StringReader(instance.getAsString());
          } else {
            content =
                new InputStreamReader(new ByteArrayInputStream(b), StandardCharsets.ISO_8859_1);
          }
          try (Reader r = content) {
            JSON.parse(r);
          } catch (IOException | JsonParseException ex) {
            // Ignore and fail
            context.addError(false, "does not validate against application/json");
            return false;
          }
        }
      } else {
        context.addError(false, "invalid media type: " + value.getAsString());
        return false;
      }
    }

    context.addAnnotation(NAME, value.getAsString());
    return true;
  }
}
