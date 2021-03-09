/*
 * Snow, a JSON Schema validator
 * Copyright (c) 2020-2021  Shawn Silverman
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
 * Created by shawn on 10/24/20 6:33 PM.
 */
package com.qindesign.json.schema.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Translates ECMA-262 patterns into Java patterns. This only performs
 * best-effort attempts using a small number of rules. It should be considered
 * a rudimentary translator.
 * <p>
 * This uses the 11th edition (June 2020).
 *
 * @see <a href="https://www.ecma-international.org/ecma-262/11.0/index.html#title">ECMA-262 edition 11</a>
 */
public class Ecma262Pattern {
  // Disallow instantiation
  private Ecma262Pattern() {
  }

  private static final String WHITESPACE_CLASS;
  private static final String NON_WHITESPACE_CLASS;

  static {
    // Gather characters from the WhiteSpace and LineTerminator productions
    // https://www.ecma-international.org/ecma-262/11.0/index.html#prod-WhiteSpace

    // Note: We can't use Java's "\h" (horizontal whitespace) because that includes \u180e
    //       We also can't use Java's "\v" (vertical whitespace) because that includes \x85

    // Gather <USP>
    Set<Character> WHITESPACE_CHARS = IntStream.range(0, 1 << 16)
        .filter(i -> Character.getType(i) == Character.SPACE_SEPARATOR)
        .boxed()
        .map(i -> (char) i.intValue())
        .collect(Collectors.toSet());
    WHITESPACE_CHARS.add('\t');      // <TAB> or <HT>
    WHITESPACE_CHARS.add('\u000B');  // <VT>
    WHITESPACE_CHARS.add('\f');      // <FF>
    WHITESPACE_CHARS.add(' ');       // <SP>
    WHITESPACE_CHARS.add('\u00A0');  // <NBSP>
    WHITESPACE_CHARS.add('\uFEFF');  // <ZWNBSP>

    // Gather LineTerminator characters
    WHITESPACE_CHARS.add('\n');      // <LF>
    WHITESPACE_CHARS.add('\r');      // <CR>
    WHITESPACE_CHARS.add('\u2028');  // <LS>
    WHITESPACE_CHARS.add('\u2029');  // <PS>

    // Construct the regex string
    StringBuilder buf = new StringBuilder();
    WHITESPACE_CHARS.stream()
        .sorted()
        .forEach(c -> buf.append("\\u").append(String.format("%04X", (int) c)));
    WHITESPACE_CLASS = "[" + buf + "]";
    NON_WHITESPACE_CLASS = "[^" + buf + "]";
  }

  /**
   * Makes a best-effort attempt to translate some ECMA-262 regex things into
   * their Java equivalents. What this translates:
   * <ol>
   * <li>Lower-case "\cX" escapes (control) into upper-case because ECMA-262
   *     allows both forms</li>
   * <li>"\s" into the proper set of WhiteSpace and LineTerminator characters</li>
   * <li>"\S" into the opposite of "\s"</li>
   * <li>"\0" into "\u0000". If the following character is a decimal digit then
   *     this will throw a {@link PatternSyntaxException}.</li>
   * <li>"$" into "\z" because "$" is only up to EOL in Java (when using
   *     {@link Matcher#find()}, which is what JavaScript regex matching does
   *     and what we're using here; {@link Matcher#matches()} matches to the end
   *     unless the {@link java.util.regex.Pattern#MULTILINE} flag is set), and
   *     ECMA-262 matches EOF, unless Multiline=true</li>
   * <li>All IdentityEscape alphabetic characters ([a-zA-Z]) are unescaped by
   *     removing the preceding backslash</li>
   * </ol>
   *
   * @param regex the regex to translate
   * @return the translated regex.
   * @throws PatternSyntaxException if the pattern has an error.
   * @see <a href="https://www.ecma-international.org/publications/standards/Ecma-262.htm">Standard ECMA-262</a>
   */
  public static String translate(String regex) {
    StringBuilder buf = new StringBuilder();
    int len = regex.length();
    for (int i = 0; i < len; i++) {
      char c = regex.charAt(i);
      if (c == '\\') {
        if (i + 1 < len) {
          i++;
          switch (c = regex.charAt(i)) {
            case 'c':
              if (i + 1 < len) {
                i++;
                c = regex.charAt(i);
                if ('a' <= c && c <= 'z') {
                  buf.append("\\c").append((char) ('A' + (c - 'a')));
                } else {
                  buf.append("\\c").append(c);
                }
              } else {
                buf.append("\\c");
              }
              break;

            case 's':
              buf.append(WHITESPACE_CLASS);
              break;
            case 'S':
              buf.append(NON_WHITESPACE_CLASS);
              break;

            case 'b': case 'B':
              // Assertion or ClassEscape

            case '1': case '2': case '3': case '4': case '5': case '6':
            case '7': case '8': case '9':
              // DecimalEscape
            case 'd': case 'D': case 'w': case 'W': case 'p': case 'P':
              // CharacterClassEscape
            case 'f': case 'n': case 'r': case 't':  // ControlEscape
            case 'x':  // Potential HexEscapeSequence
            case 'u':  // Potential RegExpUnicodeEscapeSequence
            case '/':  // IdentityEscape
            case '^': case '$': case '\\': case '.': case '*': case '+': case '?':
            case '(': case ')': case '[': case ']': case '{': case '}': case '|':
            case 'k':  // GroupName
              // Control, hex, unicode, syntax, other escapes
              // Note: None of these are in the 'default' case because that
              //       needs to check for Unicode ID_Continue characters, and
              //       some of the above characters fall into that category
              buf.append('\\').append(c);
              break;

            case 'v':
              // ControlEscape
              buf.append("\\u000B");
              break;

            case '0':
              // \0 followed by a decimal digit is legal in Java but not
              // in ECMA-262
              if (i + 1 < len && "0123456789".indexOf(regex.charAt(i + 1)) >= 0) {
                throw new PatternSyntaxException("NUL can't be followed by a decimal digit",
                                                 regex,
                                                 i + 1);
              }
              buf.append("\\u0000");
              break;

            default:
              // Escaped ID_Continue characters in Java are legal, but not legal
              // in ECMA-262
              if (Character.isUnicodeIdentifierPart(c)) {
                throw new PatternSyntaxException("ID_Continue characters not allowed", regex, i);
              }
              // Don't prepend a backslash because these are identity escapes
              // and Java says it's an error to escape alphabetic characters
              // that don't denote an escaped construct
              if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
                buf.append(c);
              } else {
                // Otherwise, we can keep the backslash
                buf.append('\\').append(c);
              }
          }
        } else {
          buf.append('\\');
        }
      } else if (c == '$') {
        buf.append("\\z");
      } else {
        buf.append(c);
      }
    }

    return buf.toString();
  }
}
