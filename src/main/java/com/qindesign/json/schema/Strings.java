/*
 * Created by shawn on 5/2/20 12:59 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * String utilities.
 */
public final class Strings {
  private Strings() {
  }

  private static final String VALID_FRAGMENT_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@/?";
  private static final String HEX_CHARS = "01234567890ABCDEFabcdef";

  private static final BitSet validHex;
  private static final BitSet validFragment;

  static {
    validHex = toASCIIBitSet(HEX_CHARS);
    validFragment = toASCIIBitSet(VALID_FRAGMENT_CHARS);
  }

  /**
   * Converts the given string to JSON form and returns it. If there was any
   * internal error then this returns the string itself.
   *
   * @param s the string to convert
   * @return the converted string.
   */
  public static String jsonString(String s) {
    StringBuilder sb = new StringBuilder();
    s.chars().forEach(c -> {
      switch (c) {
        case '\"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case'\n':
          sb.append("\\n");
          break;
        case'\r':
          sb.append("\\r");
          break;
        case'\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", c));
          } else {
            sb.append(c);
          }
      }
    });
    return sb.toString();
  }

  /**
   * Converts a string into a {@link BitSet} having '1's for all indexes equal
   * to the characters in the string.
   * <p>
   * The set will have 128 bits.
   *
   * @param s the string to analyze
   * @throws IllegalArgumentException if the string contains any characters
   *         outside of US-ASCII.
   */
  private static BitSet toASCIIBitSet(String s) {
    BitSet bs = new BitSet(128);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= 128) {
        throw new IllegalArgumentException(
            "Character not ASCII[" + i + "]: 0x" + String.format("%04x", (int) c));
      }
      bs.set(c);
    }
    return bs;
  }

  /**
   * Converts a JSON pointer to URI form, as a fragment. There's a small chance
   * that this may throw an {@link IllegalArgumentException} if there was some
   * encoding error.
   *
   * @param ptr the JSON pointer
   * @return the pointer in URI form.
   * @throws IllegalArgumentException if there was some encoding error, a
   *         small chance.
   */
  public static URI jsonPointerToURI(String ptr) {
    return URI.create("#" + Strings.pctEncodeFragment(ptr));
  }

  /**
   * Converts a URI fragment to a JSON pointer by un-escaping all percent-
   * encoded characters. This assumes correctness.
   *
   * @param fragment the URI fragment
   * @return the JSON pointer form of the URI fragment.
   */
  public static String fragmentToJSONPointer(String fragment) {
    return URLDecoder.decode(fragment, StandardCharsets.UTF_8);
  }

  /**
   * Tests if the given code point is a hex digit.
   *
   * @param c the code point
   * @return whether the code point is a hex digit.
   */
  public static boolean isHex(int c) {
    return (c < 128) && validHex.get(c);
  }

  /**
   * Encodes a string into a valid URI fragment. If any percent-encoded
   * characters are found then they are checked for a valid format. Any that are
   * invalid have their percent signs ('%') encoded as "%25" (the code for '%').
   *
   * @param s the string to encode
   * @return the encoded value.
   * @see <a href="https://tools.ietf.org/html/rfc3986#section-3.5">Uniform Resource Identifier (URI): Generic Syntax: 3.5. Fragment</a>
   */
  public static String pctEncodeFragment(String s) {
    StringBuilder sb = new StringBuilder();
    int[] pctState = { -1 };  // Array is to make it accessible from the lambda
    s.codePoints().forEach(c -> {
      if (pctState[0] >= 0) {
        if (pctState[0] == '%') {
          if (isHex(c)) {
            pctState[0] = c;
            return;
          }
          sb.append("%25");
          // Keep pctState at '%'
        } else if (isHex(c)) {
          sb.append('%').append((char) pctState[0]).append((char) c);
          pctState[0] = -1;
          return;
        } else {
          // pctState is a hex value
          sb.append("%25").append((char) pctState[0]);
          pctState[0] = -1;
        }
      }

      if ((c < 128) && validFragment.get(c)) {
        sb.append((char) c);
        return;
      }
      if ('%' != c) {
        sb.append(c);
        return;
      }
      pctState[0] = '%';
    });

    // Write any buffered characters
    if (pctState[0] >= 0) {
      if (pctState[0] == '%') {
        sb.append("%25");
      } else {
        sb.append("%25").append((char) pctState[0]);
      }
    }

    return sb.toString();
  }
}
