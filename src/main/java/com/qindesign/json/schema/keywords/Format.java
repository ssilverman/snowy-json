/*
 * Created by shawn on 5/4/20 8:31 AM.
 */
package com.qindesign.json.schema.keywords;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.google.gson.JsonElement;
import com.qindesign.json.schema.Keyword;
import com.qindesign.json.schema.MalformedSchemaException;
import com.qindesign.json.schema.Validator;
import com.qindesign.json.schema.ValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the "format" assertion.
 */
public class Format extends Keyword {
  public static final String NAME = "format";

  // Data/time: https://tools.ietf.org/html/rfc3339#section-5.6
  // Duration: https://tools.ietf.org/html/rfc3339#appendix-A

  // https://mattallan.me/posts/rfc3339-date-time-validation/
  // https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html

  private static final String FULL_DATE_PATTERN =
      "(?<year>[0-9]{4})-(?<month>0[1-9]|1[0-2])-(?<mday>0[1-9]|[12][0-9]|3[01])";
  private static final String FULL_TIME_PATTERN =
      "(?:[01][0-9]|2[0-3]):[0-5][0-9]:(?:[0-5][0-9]|60)(?:\\.[0-9]+)?" +
      "(?:Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])";
  private static final String DATE_TIME_PATTERN = FULL_DATE_PATTERN + "T" + FULL_TIME_PATTERN;

  private static final java.util.regex.Pattern DATE_TIME =
      java.util.regex.Pattern.compile("^" + DATE_TIME_PATTERN + "$");

  private static final java.util.regex.Pattern FULL_DATE =
      java.util.regex.Pattern.compile("^" + FULL_DATE_PATTERN + "$");

  private static final java.util.regex.Pattern FULL_TIME =
      java.util.regex.Pattern.compile("^" + FULL_TIME_PATTERN + "$");

  private static final int[] MDAY_MAX = {
      0,
      31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
  };

  private static final String DUR_SECOND = "\\d+S";
  private static final String DUR_MINUTE = "\\d+M(?:" + DUR_SECOND + ")?";
  private static final String DUR_HOUR = "\\d+H(?:" + DUR_MINUTE + ")?";
  private static final String DUR_TIME =
      "T(?:" + DUR_HOUR + "|" + DUR_MINUTE + "|" + DUR_SECOND + ")";

  private static final String DUR_DAY = "\\d+D";
  private static final String DUR_WEEK = "\\d+W";
  private static final String DUR_MONTH = "\\d+M(?:" + DUR_DAY + ")?";
  private static final String DUR_YEAR = "\\d+Y(?:" + DUR_MONTH + ")?";
  private static final String DUR_DATE =
      "(?:" + DUR_DAY + "|" + DUR_MONTH + "|" + DUR_YEAR + ")(?:" + DUR_TIME + ")?";

  private static final java.util.regex.Pattern DURATION =
      java.util.regex.Pattern
          .compile("^P(?:" + DUR_DATE + "|" + DUR_TIME + "|" + DUR_WEEK + ")$");

  private static final java.util.regex.Pattern EMAIL =
      java.util.regex.Pattern.compile("^[^@]+@[^@]+$");

  private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  // URI templates
  private static final String TEMPLATE_VAR = "(?!\\.)(?:\\.?(\\w|%\\p{XDigit}{2}))+";
  private static final String TEMPLATE_MOD = ":[1-9]\\d{0,3}";
  private static final String TEMPLATE_VARSPEC = TEMPLATE_VAR + "(?:" + TEMPLATE_MOD + ")?";
  private static final String TEMPLATE_VARLIST = "(?!,)(?:,?" + TEMPLATE_VARSPEC + ")+";
  private static final java.util.regex.Pattern TEMPLATE_EXPR =
      java.util.regex.Pattern.compile("^[+#./;?&=,!@|]?" + TEMPLATE_VARLIST + "$");

  /** Quick check characters not supposed to be in a hostname. */
  private static final java.util.regex.Pattern NON_HOSTNAME =
      java.util.regex.Pattern.compile("[^a-zA-Z0-9.-]");

  private static final java.util.regex.Pattern JSON_POINTER_PATTERN =
      java.util.regex.Pattern.compile("(?:/(?:[^/~]|~[01])*)*");
  static final java.util.regex.Pattern JSON_POINTER =
      java.util.regex.Pattern.compile("^" + JSON_POINTER_PATTERN + "$");

  private static final java.util.regex.Pattern RELATIVE_JSON_POINTER =
      java.util.regex.Pattern.compile("^(?:0|[1-9]\\d*)(?:#|" + JSON_POINTER_PATTERN + ")$");

  public Format() {
    super(NAME);
  }

  /**
   * Returns whether the given year is a leap year.
   *
   * @param year the year to check
   */
  private static boolean isLeapYear(int year) {
    if (year % 4 != 0) {
      return false;
    }
    if (year % 100 != 0) {
      return true;
    }
    if (year % 400 != 0) {
      return false;
    }
    return true;
  }

  /**
   * Appends a byte as a percent-hex-encoded value. This assumes that {@code v}
   * is one byte.
   *
   * @param sb the buffer to append to
   * @param v the byte value to append
   */
  private static void appendPctHex(StringBuilder sb, int v) {
    sb.append('%');
    sb.append(HEX_DIGITS[v >> 4]);
    sb.append(HEX_DIGITS[v & 0x0f]);
  }

  /**
   * Converts an IRI to a URI per the IRI-to-URI rules.
   *
   * @param iri the IRI
   * @return the converted value.
   * @see <a href="https://tools.ietf.org/html/rfc3987#section-3.1">RFC 3987: 3.1. Mapping of IRIs to URIs</a>
   */
  private static String iriToURI(String iri) {
    StringBuilder sb = new StringBuilder();
    iri.codePoints().forEach(c -> {
      // Only encode ucschar and iprivate characters
      // See: https://tools.ietf.org/html/rfc3629
      if (c < 0x80) {
        sb.append((char) c);
      } else if (c < 0x800) {
        if (c >= 0xa0) {
          appendPctHex(sb, 0xc0 | (c >> 6));
          appendPctHex(sb, 0x80 | (c & 0x3f));
        } else {
          sb.appendCodePoint(c);
        }
      } else if (c < 0x10000) {
        if (c <= 0xd7ff || (0xe000 <= c && c < 0xfdd0) || (0xfdf0 <= c && c < 0xfff0)) {
          appendPctHex(sb, 0xe0 | (c >> 12));
          appendPctHex(sb, 0x80 | ((c >> 6) & 0x3f));
          appendPctHex(sb, 0x80 | (c & 0x3f));
        } else {
          sb.appendCodePoint(c);
        }
      } else if (c < 0x110000) {
        if ((c & 0xfffe) == 0xfffe || (0xe0000 <= c && c < 0xe1000)) {
          sb.appendCodePoint(c);
        } else {
          appendPctHex(sb, 0xf0 | (c >> 18));
          appendPctHex(sb, 0x80 | ((c >> 12) & 0x3f));
          appendPctHex(sb, 0x80 | ((c >> 6) & 0x3f));
          appendPctHex(sb, 0x80 | (c & 0x3f));
        }
      }
    });
    return sb.toString();
  }

  /**
   * Checks the syntax of a URI-Template.
   *
   * @param s the string to check
   * @return whether the string contains a valid URI-Template.
   * @see <a href="https://tools.ietf.org/html/rfc6570#section-2">RFC 6570: 2. Syntax</a>
   */
  private static boolean checkURITemplate(String s) {
    int exprIndex = -1;

    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
        case '{':
          if (exprIndex >= 0) {
            return false;
          }
          exprIndex = i + 1;
          break;
        case '}':
          if (exprIndex < 0) {
            return false;
          }

          // Parse the expression
          if (!TEMPLATE_EXPR.matcher(s.substring(exprIndex, i)).matches()) {
            return false;
          }

          exprIndex = -1;
          break;
        default:
          // Nothing because any non-literal characters will be percent-encoded
      }
    }
    return (exprIndex < 0);
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  protected boolean apply(JsonElement value, JsonElement instance, ValidatorContext context)
      throws MalformedSchemaException {
    if (!Validator.isString(value)) {
      context.schemaError("not a string");
      return false;
    }

    switch (value.getAsString()) {
      case "date-time":
      case "date":
      case "full-date":
        if (!Validator.isString(instance)) {
          return true;
        }
        Matcher m;
        if (value.getAsString().equals("date-time")) {
          m = DATE_TIME.matcher(instance.getAsString());
        } else {
          m = FULL_DATE.matcher(instance.getAsString());
        }
        if (!m.matches()) {
          return false;
        }

        // Check the month and mday
        try {
          int month = Integer.parseInt(m.group("month"));
          int mday = Integer.parseInt(m.group("mday"));
          if (mday > MDAY_MAX[month]) {
            return false;
          }

          // Special handling for February
          if (month == 2) {
            int year = Integer.parseInt(m.group("year"));
            if (!isLeapYear(year)) {
              return mday <= 28;
            }
          }
          return true;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
          return true;
        }
      case "time":
      case "full-time":
        if (!Validator.isString(instance)) {
          return true;
        }
        return FULL_TIME.matcher(instance.getAsString()).matches();
      case "duration":
        if (!Validator.isString(instance)) {
          return true;
        }
        return DURATION.matcher(instance.getAsString()).matches();
      case "email":
      case "idn-email":
        if (!Validator.isString(instance)) {
          return true;
        }
//        // For now, just check for one '@' sign not at the ends
//        int atIndex = instance.getAsString().indexOf('@');
//        if (0 < atIndex && atIndex < instance.getAsString().length() - 1 &&
//            instance.getAsString().indexOf('@', atIndex + 1) < 0) {
//          return true;
//        }
//        return false;
        return EMAIL.matcher(instance.getAsString()).matches();
      case "hostname":
      case "idn-hostname":
        if (!Validator.isString(instance)) {
          return true;
        }
        // TODO: Allow IPv6 addresses here?
        if (value.getAsString().equals("hostname")) {
          if (NON_HOSTNAME.matcher(instance.getAsString()).matches()) {
            return false;
          }
        }
        return InternetDomainName.isValid(instance.getAsString());
      case "ipv4":
      case "ipv6":
        if (!Validator.isString(instance)) {
          return true;
        }
        try {
          int len;
          if (value.getAsString().equals("ipv4")) {
            len = 4;
          } else {
            len = 16;
          }
          return InetAddresses.forString(instance.getAsString()).getAddress().length == len;
        } catch (IllegalArgumentException ex) {
          return false;
        }
      case "uri":
      case "uri-reference":
        if (!Validator.isString(instance)) {
          return true;
        }
        try {
          URI uri = new URI(instance.getAsString());
          if (value.getAsString().equals("uri")) {
            return uri.isAbsolute();
          }
          return true;
        } catch (URISyntaxException ex) {
          return false;
        }
      case "iri":
      case "iri-reference":
        if (!Validator.isString(instance)) {
          return true;
        }
        try {
          URI uri = new URI(iriToURI(instance.getAsString()));
          if (value.getAsString().equals("iri")) {
            return uri.isAbsolute();
          }
          return true;
        } catch (URISyntaxException ex) {
          return false;
        }
      case "uuid":
        if (!Validator.isString(instance)) {
          return true;
        }
        try {
          UUID.fromString(instance.getAsString());
        } catch (IllegalArgumentException ex) {
          return false;
        }
      case "uri-template":
        if (!Validator.isString(instance)) {
          return true;
        }
        return checkURITemplate(instance.getAsString());
      case "json-pointer":
        if (!Validator.isString(instance)) {
          return true;
        }
        return JSON_POINTER.matcher(instance.getAsString()).matches();
      case "relative-json-pointer":
        if (!Validator.isString(instance)) {
          return true;
        }
        return RELATIVE_JSON_POINTER.matcher(instance.getAsString()).matches();
      case "regex":
        if (!Validator.isString(instance)) {
          return true;
        }
        try {
          java.util.regex.Pattern.compile(instance.getAsString());
          return true;
        } catch (PatternSyntaxException ex) {
          return false;
        }
      default:
        return true;
    }
  }
}
