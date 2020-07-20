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

import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
import java.util.Arrays;
import java.util.BitSet;

/**
 * String utilities.
 */
public final class Strings {
  /**
   * Disallow instantiation.
   */
  private Strings() {
  }

  private static final String VALID_FRAGMENT_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@/?";
  private static final String HEX_CHARS = "01234567890ABCDEFabcdef";

  /** Hex digits for percent-encoding. */
  private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private static final BitSet validHex;
  private static final BitSet validFragment;

  static {
    validHex = toASCIIBitSet(HEX_CHARS);
    validFragment = toASCIIBitSet(VALID_FRAGMENT_CHARS);
  }

  /**
   * Converts the given string to JSON form and returns it. If there was any
   * internal error, then this returns the string itself.
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
    try {
      return new URI(null, null, null, null, ptr);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Unexpected bad URI", ex);
    }
  }

  /**
   * Encodes the string into a valid JSON Pointer "reference-token". This
   * converts all '~' and '/' to '~0' and '~1', respectively. To emphasize this
   * method's purpose: It is only designed to encode path elements of a JSON
   * Pointer &mdash; the parts between the '/', and not the whole thing because
   * '/' characters will get encoded.
   *
   * @param s the token to encode
   * @return the JSON Pointer-encoded token.
   */
  public static String jsonPointerToken(String s) {
    return s.replace("~", "~0").replace("/", "~1");
  }

  /**
   * Decodes a JSON Pointer "reference-token". This converts all '~0' and '~1'
   * to '~' and '/', respectively.
   *
   * @param s the token to dencode
   * @return the decoded token.
   * @throws IllegalArgumentException if the token is not valid.
   */
  public static String fromJSONPointerToken(String s) {
    // Note: This does not work because the first transform may result in a
    //       string having '~1':
//    return s.replace("~0", "~").replace("~1", "/");

    if (s.indexOf('/') >= 0) {
      throw new IllegalArgumentException("Bad character ('/'): " + s);
    }
    if (s.indexOf('~') < 0) {
      return s;
    }

    StringBuilder sb = new StringBuilder(s.length());

    int[] tildeState = { -1 };
    s.codePoints().forEach(c -> {
      if (tildeState[0] == '~') {
        if (c == '0') {
          sb.append('~');
        } else if (c == '1') {
          sb.append('/');
        } else {
          throw new IllegalArgumentException(
              "Bad escape character (0x" + Integer.toHexString(c) + "): " + s);
        }
        tildeState[0] = -1;
        return;
      }

      if (c == '~') {
        tildeState[0] = '~';
        return;
      }

      sb.appendCodePoint(c);
    });

    // Check that there's no lone '~' at the end
    if (tildeState[0] >= 0) {
      throw new IllegalArgumentException("Extra '~': " + s);
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
