/*
 * Created by shawn on 5/2/20 12:59 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
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
    try (StringWriter sw = new StringWriter(s.length()); JsonWriter jw = new JsonWriter(sw)) {
      jw.value(s);
      jw.flush();
      return sw.toString();
    } catch (IOException ex) {
      return s;
    }
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
   * characters are found then they are checked for a valid format. An
   * {@link IllegalArgumentException} is thrown upon encountering any that
   * are invalid.
   *
   * @param s the string to encode
   * @return the encoded value.
   * @throws IllegalArgumentException if a bad percent encoding was found.
   * @see <a href="https://tools.ietf.org/html/rfc3986#section-3.5">Uniform Resource Identifier (URI): Generic Syntax: 3.5. Fragment</a>
   */
  public static String pctEncodeFragment(String s) {
    StringBuilder sb = new StringBuilder(s);
    int[] pctState = { -1 };  // Array is to make it accessible from the lambda
    s.codePoints().forEach(c -> {
      if (pctState[0] >= 0) {
        if (pctState[0] == '%') {
          if (isHex(c)) {
            pctState[0] = c;
            return;
          }
        } else if (isHex(c)) {
          sb.append('%').append((char) pctState[0]).append((char) c);
          pctState[0] = -1;
          return;
        }
        throw new IllegalArgumentException("Invalid fragment: " + s);
      }

      if ((c < 128) && validFragment.get(c)) {
        sb.appendCodePoint(c);
        return;
      }
      if ('%' != c) {
        sb.appendCodePoint(c);
        return;
      }
      pctState[0] = '%';
    });
    return sb.toString();
  }
}
