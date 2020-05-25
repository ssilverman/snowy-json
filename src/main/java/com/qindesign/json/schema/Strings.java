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
 * Created by shawn on 5/2/20 12:59 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
            sb.append((char) c);
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
   * Converts a JSON Pointer to URI form, as a fragment. There's a small chance
   * that this may throw an {@link IllegalArgumentException} if there was some
   * encoding error.
   *
   * @param ptr the JSON Pointer
   * @return the pointer in URI form.
   * @throws IllegalArgumentException if there was some encoding error, a
   *         small chance.
   */
  public static URI jsonPointerToURI(String ptr) {
    return URI.create("#" + Strings.pctEncodeFragment(ptr));
  }

  /**
   * Converts a URI fragment to a JSON Pointer by un-escaping all percent-
   * encoded characters. This assumes correctness.
   *
   * @param fragment the URI fragment
   * @return the JSON Pointer form of the URI fragment.
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

  private static final char[] BASE64_ALPHABET = {
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
      'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
      'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
      'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
  };

  /**
   * Base64 alphabet bits. -1 means not defined and -2 is the padding character.
   * Each value is otherwise the 6-bit Base64 value. This includes the URL and
   * filename safe alphabet, where '-' and '_' represent the values 62 and
   * 63, respectively.
   */
  public static final int[] BASE64_BITS = new int[256];

  static {
    Arrays.fill(BASE64_BITS, -1);
    for (int i = 0; i < BASE64_ALPHABET.length; i++) {
      BASE64_BITS[BASE64_ALPHABET[i]] = i;
    }
    // Add the URL-safe characters
    BASE64_BITS['-'] = 62;
    BASE64_BITS['_'] = 63;
    // Add the padding
    BASE64_BITS['='] = -2;
  }

  /**
   * Checks if a string is a valid Base64 value. Note that this allows a padding
   * value of "====". This allows both regular Base64 and URl and filename
   * safe Base64.
   *
   * @param s the Base64 string
   * @return whether the string is a valid Base64 value.
   */
  public static boolean isBase64(String s) {
    if ((s.length() & 0x03) != 0) {
      return false;
    }
    char[] buf = new char[4];
    boolean end = false;
    for (int i = 0; i < s.length(); i += 4) {
      if (end) {
        return false;
      }
      s.getChars(i, i + 4, buf, 0);

      // Check that the high bytes are all zero
      if ((buf[0] | buf[1] | buf[2] | buf[3]) > 0xff) {
        return false;
      }
      int b0 = BASE64_BITS[buf[0]];
      int b1 = BASE64_BITS[buf[1]];
      int b2 = BASE64_BITS[buf[2]];
      int b3 = BASE64_BITS[buf[3]];
      if ((b0 | b1 | b2 | b3) < 0) {
        if (b0 < 0 || b1 < 0) {
          // Allow "====" as a padding
          if (b0 != -2 || b1 != -2 || b2 != -2 || b3 != -2) {
            return false;
          }
        } else if (b2 < 0) {
          if (b2 == -2) {
            if (b3 != -2) {
              return false;
            }
            // 8 bits
          } else {
            return false;
          }
        } else if (b3 != -2) {
          return false;
        }
        // 16 bits
        end = true;
      }
    }

    return true;
  }
}
