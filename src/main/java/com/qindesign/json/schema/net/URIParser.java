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
 * Created by shawn on 6/3/20 10:07 PM.
 */
package com.qindesign.json.schema.net;

import java.util.BitSet;

/**
 * Parses a URI according to RFC 3986.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html">RFC 3986: Uniform Resource Identifier (URI): Generic Syntax</a>
 */
public final class URIParser {
  private static final BitSet ALPHA = new BitSet(128);
  private static final BitSet DIGIT = new BitSet(128);
  private static final BitSet HEXDIG = new BitSet(128);
  private static final BitSet UNRESERVED = new BitSet(128);
  private static final BitSet SUB_DELIMS = new BitSet(128);
  private static final BitSet SCHEME = new BitSet(128);
  static final BitSet USERINFO = new BitSet(128);
  private static final BitSet IPVFUTURE = new BitSet(128);
  static final BitSet REG_NAME = new BitSet(128);
  static final BitSet PATH = new BitSet(128);
  static final BitSet QUERY_OR_FRAGMENT = new BitSet(128);

  static {
    ALPHA.set('A', 'Z' + 1);
    ALPHA.set('a', 'z' + 1);
    DIGIT.set('0', '9' + 1);
    HEXDIG.set('0', '9' + 1);
    HEXDIG.set('A', 'F' + 1);
    HEXDIG.set('a', 'f' + 1);

    UNRESERVED.or(ALPHA);
    UNRESERVED.or(DIGIT);
    UNRESERVED.set('-');
    UNRESERVED.set('.');
    UNRESERVED.set('_');
    UNRESERVED.set('~');

    SUB_DELIMS.set('!');
    SUB_DELIMS.set('$');
    SUB_DELIMS.set('&');
    SUB_DELIMS.set('\'');
    SUB_DELIMS.set('(');
    SUB_DELIMS.set(')');
    SUB_DELIMS.set('*');
    SUB_DELIMS.set('+');
    SUB_DELIMS.set(',');
    SUB_DELIMS.set(';');
    SUB_DELIMS.set('=');

    SCHEME.or(ALPHA);
    SCHEME.or(DIGIT);
    SCHEME.set('0', '9' + 1);
    SCHEME.set('+');
    SCHEME.set('-');
    SCHEME.set('.');

    USERINFO.or(UNRESERVED);
    USERINFO.or(SUB_DELIMS);
    USERINFO.set(':');
    USERINFO.set('%');  // Special handling

    IPVFUTURE.or(UNRESERVED);
    IPVFUTURE.or(SUB_DELIMS);
    IPVFUTURE.set(':');

    REG_NAME.or(UNRESERVED);
    REG_NAME.or(SUB_DELIMS);
    REG_NAME.set('%');  // Special handling

    PATH.or(UNRESERVED);
    PATH.or(SUB_DELIMS);
    PATH.set(':');
    PATH.set('@');
    PATH.set('/');
    PATH.set('%');  // Special handling

    QUERY_OR_FRAGMENT.or(UNRESERVED);
    QUERY_OR_FRAGMENT.or(SUB_DELIMS);
    QUERY_OR_FRAGMENT.set(':');
    QUERY_OR_FRAGMENT.set('@');
    QUERY_OR_FRAGMENT.set('/');
    QUERY_OR_FRAGMENT.set('?');
    QUERY_OR_FRAGMENT.set('%');  // Special handling
  }

  private final String s;

  String scheme = null;
  String authority = null;
  String path = "";
  String query = null;
  String fragment = null;

  // Parts of authority, not set if authority not set
  String userInfo = null;
  String host = null;
  int port = -1;

  /**
   * Creates a new URI parser for the given input string.
   *
   * @param s the input string
   */
  URIParser(String s) {
    this.s = s;
  }

  /**
   * Scans the string until it encounters a character in {@code stopChars}. If
   * it encounters a character in {@code stopChars}, then this returns the index
   * of that character. If no special character is encountered, then this
   * returns the end.
   *
   * @param s the string to scan
   * @param start the starting index
   * @param end the ending index
   * @param stopChars the stop characters
   * @return the index of any found stop character or the end if no special
   *         characters were encountered.
   */
  private static int scan(String s, int start, int end, String stopChars) {
    for ( ; start < end; start++) {
      char c = s.charAt(start);
      if (stopChars.indexOf(c) >= 0) {
        return start;
      }
    }
    return end;
  }

  /**
   * Checks that all the characters in the string match the characters in
   * {@code chars}. This handles '%' specially as a percent-encoded character if
   * it's in the set.
   *
   * @param s the string to check
   * @param start the starting index
   * @param end the ending index
   * @param chars the check characters
   * @param failMsg the fail message
   * @throws URISyntaxException if the check failed.
   */
  private static void check(String s, int start, int end, BitSet chars, String failMsg)
      throws URISyntaxException {
    for ( ; start < end; start++) {
      char c = s.charAt(start);
      if (c < 128) {
        if (!chars.get(c)) {
          throw new URISyntaxException(s, failMsg, start);
        }
        if (c == '%') {
          if (start + 3 > end) {
            throw new URISyntaxException(s, failMsg + ": unfinished percent-encoding", start);
          }
          check(s, start + 1, start + 3, HEXDIG, failMsg + ": bad percent-encoding");
          start += 2;
        }
      } else {
        throw new URISyntaxException(s, failMsg, start);
      }
    }
  }

  /**
   * Parses the input string.
   *
   * @return the parsed {@link URI}.
   * @throws URISyntaxException if there was a parsing error.
   */
  URI parse() throws URISyntaxException {
    int len = s.length();

    // Scheme
    int index = scan(s, 0, len, ":/?#");
    if (index < len) {
      if (s.charAt(index) != ':') {
        index = 0;
      } else {
        if (index == 0) {
          throw new URISyntaxException(s, "Empty scheme", 0);
        }
        check(s, 0, 1, ALPHA, "Bad scheme");
        check(s, 1, index, SCHEME, "Bad scheme");
        scheme = s.substring(0, index);
        index++;
      }
    } else {
      index = 0;
    }

    // Authority
    if (s.startsWith("//", index)) {
      int startIndex = index + 2;
      index = scan(s, startIndex, len, "/?#");
      parseAuthority(startIndex, index, "Bad authority");
    }

    // Path
    int startIndex = index;
    index = scan(s, startIndex, len, "?#");
    check(s, startIndex, index, PATH, "Bad path");
    path = s.substring(startIndex, index);

    if (index < len) {
      // Query and fragment
      if (s.charAt(index) == '?') {
        startIndex = index + 1;
        index = s.indexOf('#', startIndex);
        if (index < 0) {
          index = len;
        }
        check(s, startIndex, index, QUERY_OR_FRAGMENT, "Bad query");
        query = s.substring(startIndex, index);
      }

      if (index < len) {
        startIndex = index + 1;
        check(s, startIndex, len, QUERY_OR_FRAGMENT, "Bad fragment");
        fragment = s.substring(startIndex);
      }
    }

    return new URI(scheme, authority, userInfo, host, port, path, query, fragment);
  }

  /**
   * Parses the authority.
   *
   * @param start the starting index, inclusive
   * @param end the ending index, exclusive
   * @param failMsg the failure message, for any failures
   * @throws URISyntaxException if there was a parsing error.
   */
  private void parseAuthority(int start, int end, String failMsg) throws URISyntaxException {
    // Userinfo
    int index = s.indexOf('@', start);
    if (0 <= index && index < end) {
      check(s, start, index, USERINFO, failMsg + ": bad userinfo");
      userInfo = s.substring(start, index);
      index++;
    } else {
      index = start;
    }

    // Host
    if (index < end) {
      String failMsgHost = failMsg + ": bad host";
      if (s.charAt(index) == '[') {
        index++;
        int bracketIndex = s.indexOf(']', index);
        if (bracketIndex < 0 || end <= bracketIndex) {
          throw new URISyntaxException(s, failMsgHost + ": missing end bracket", index);
        }
        if (bracketIndex - index < 2) {
          throw new URISyntaxException(s, failMsgHost + ": bad IP-literal", index);
        }

        // IPv6address or IPvFuture
        host = s.substring(index - 1, bracketIndex + 1);
        if (s.charAt(index) == 'v' || s.charAt(index) == 'V') {
          // IPvFuture
          String failMsgIP = failMsgHost + ": bad IPvFuture";
          index++;
          int dotIndex = s.indexOf('.', index);
          if (dotIndex < 0 || end <= dotIndex) {
            throw new URISyntaxException(s, failMsgIP + ": missing version", index);
          }
          if (dotIndex - index < 1) {
            throw new URISyntaxException(s, failMsgIP + ": empty version", index);
          }
          check(s, index, dotIndex, HEXDIG, failMsgIP + ": bad version");
          index = dotIndex + 1;
          if (bracketIndex - index < 1) {
            throw new URISyntaxException(s, failMsgIP + ": empty value", index);
          }
          check(s, index, bracketIndex, IPVFUTURE, failMsgIP + ": bad value");
        } else {
          // IPv6address
          parseIPv6(s, index, bracketIndex, failMsgHost + ": bad IPv6address");
        }

        index = bracketIndex + 1;
      } else {
        // IPv4address or reg-name
        int portIndex = s.indexOf(':', index);
        if (portIndex < 0 || end <= portIndex) {
          portIndex = end;
        }
        check(s, index, portIndex, REG_NAME, failMsgHost);
        host = s.substring(index, portIndex);
        index = portIndex;
      }
    } else {
      host = "";
    }

    if (index < end) {
      if (s.charAt(index) != ':') {
        throw new URISyntaxException(s, failMsg + ": expecting port", index);
      }
      index++;
      // May have an empty port
      if (index < end) {
        check(s, index, end, DIGIT, failMsg + ": bad port");
        port = Integer.parseInt(s, index, end, 10);
      }
    }

    authority = s.substring(start, end);
  }

  /**
   * Parses an IPv6 address.
   *
   * @param s the address to parse
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @param failMsg the failure message, for any failures
   * @throws URISyntaxException if there was a parsing error.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5952.html">RFC 5952: A Recommendation for IPv6 Address Text Representation</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-3.2.2">RFC 3986: Section 3.2.2.: Host</a>
   */
  public static void parseIPv6(String s, int start, int end, String failMsg) throws URISyntaxException {
    int partIndex = 0;
    int compressedIndex = -1;
    boolean lastPartEmpty = false;
    int wordCount = 0;

    // For each part, split by a ":"
    int index = start;
    while (index < end && partIndex < 9) {
      int partEnd = s.indexOf(':', index);
      if (partEnd < 0 || end <= partEnd) {
        // This means this is the last part
        break;
      }

      if (partEnd - index == 0) {
        // Empty part
        if (partIndex >= 1) {
          if (compressedIndex >= 0) {
            throw new URISyntaxException(s, failMsg + ": too many compressed sections", index);
          }
          compressedIndex = partIndex;
        }
        lastPartEmpty = true;
      } else {
        // Non-empty part
        if (partIndex == 1) {
          if (lastPartEmpty) {
            throw new URISyntaxException(s, failMsg + ": expecting empty start", index);
          }
        }

        if (partEnd - index > 4) {
          throw new URISyntaxException(s, failMsg + ": word[" + wordCount + "] too large", index);
        }
        check(s, index, partEnd, HEXDIG, failMsg + ": malformed word[" + wordCount + "]");
        wordCount++;

        lastPartEmpty = false;
      }

      index = partEnd + 1;
      partIndex++;
    }

    // We have reached the last part
    if (partIndex < 2 || 9 <= partIndex) {
      throw new URISyntaxException(s, failMsg + ": wrong size", start);
    }

    if (end - index == 0) {
      if (!lastPartEmpty) {
        throw new URISyntaxException(s, failMsg + ": expecting empty end", index);
      }
      return;
    }

    wordCount++;

    // Check for a possible IPv4 ending
    boolean hasIPv4 = (s.indexOf('.', index) >= 0);
    if (hasIPv4) {
      wordCount++;
    }

    if (compressedIndex >= 0) {
      if (wordCount >= 8) {
        throw new URISyntaxException(
            s, failMsg + ": got " + wordCount + " words, want < 8 words with compressed section", start);
      }
    } else {
      if (wordCount != 8) {
        throw new URISyntaxException(s, failMsg + ": got " + wordCount + " words, want 8 words", start);
      }
    }

    if (hasIPv4) {
      parseIPv4(s, index, end, failMsg + ": bad IPv4address part");
      return;
    }

    if (end - index > 4) {
      throw new URISyntaxException(s, failMsg + ": last word too large", index);
    }
    check(s, index, end, HEXDIG, failMsg + ": malformed last word");
  }

  /**
   * Parses an IPv4 address.
   *
   * @param s the address to parse
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @param failMsg the failure message, for any failures
   * @throws URISyntaxException if there was a parsing error.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-3.2.2">RFC 3986: Section 3.2.2.: Host</a>
   */
  public static void parseIPv4(String s, int start, int end, String failMsg)
      throws URISyntaxException {
    for (int i = 0; i < 4; i++) {
      int next = scan(s, start, end, ".");
      if (next <= start) {
        throw new URISyntaxException(s, failMsg + ": empty octet[" + i + "]", start);
      }
      check(s, start, next, DIGIT, failMsg + ": malformed octet[" + i + "]");
      int octet = Integer.parseInt(s, start, next, 10);
      if (octet > 255 ||
          (octet < 10 && next - start != 1) ||
          (10 <= octet && octet < 100 && next - start != 2) ||
          (octet >= 100 && next - start != 3)) {
        throw new URISyntaxException(s, failMsg + ": malformed octet[" + i + "]", start);
      }
      start = next + 1;
    }
    if (start != end + 1) {
      throw new URISyntaxException(s, failMsg + ": too many octets", start);
    }
  }
}
