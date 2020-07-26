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
 * Created by shawn on 6/12/20 4:23 PM.
 */
package com.qindesign.net;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import java.net.IDN;
import java.util.BitSet;

/**
 * Parses both regular and IDN hostnames.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc1123.html#section-2">RFC 1123: 2.1 Host Names and Numbers</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc1035.html#section-2.3.1">RFC 1035: 2.3.1. Preferred name syntax</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc1912.html#section-2.1">RFC 1912: 2.1 Inconsistent, Missing, or Bad Data</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3696.html#section-2">RFC 3696: 2. Restrictions on domain (DNS) names</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5890.html">RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5891.html">RFC 5891: Internationalized Domain Names in Applications (IDNA): Protocol</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5892.html">RFC 5892: The Unicode Code Points and Internationalized Domain Names for Applications (IDNA)</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5893.html">RFC 5893: Right-to-Left Scripts for Internationalized Domain Names for Applications (IDNA)</a>
 */
public final class Hostname {
  /**
   * Disallow instantiation.
   */
  private Hostname() {
  }

  // Character sets
  private static final BitSet HOSTNAME_LABEL = new BitSet(128);
  private static final BitSet DIGIT = new BitSet(128);

  static {
    HOSTNAME_LABEL.set('A', 'Z' + 1);
    HOSTNAME_LABEL.set('a', 'z' + 1);
    HOSTNAME_LABEL.set('0', '9' + 1);
    HOSTNAME_LABEL.set('-');

    DIGIT.set('0', '9' + 1);
  }

  /**
   * Parses a hostname. A hostname is valid if each label is valid and the last
   * label does not consist entirely of digits.
   *
   * @param s the string to parse
   * @return whether the hostname is valid.
   * @see #parseLabel(String, int, int)
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1123.html#section-2">RFC 1123: 2.1 Host Names and Numbers</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1035.html#section-2.3.1">RFC 1035: 2.3.1. Preferred name syntax</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1912.html#section-2.1">RFC 1912: 2.1 Inconsistent, Missing, or Bad Data</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3696.html#section-2">RFC 3696: 2. Restrictions on domain (DNS) names</a>
   */
  public static boolean parseHostname(String s) {
    return parseHostname(s, false);
  }

  /**
   * Parses an IDN hostname. A hostname is valid if each label is valid and the
   * last label does not consist entirely of digits.
   *
   * @param s the string to parse
   * @return whether the IDN hostname is valid.
   * @see #parseIDNLabel(String, int, int)
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5890.html">RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework</a>
   */
  public static boolean parseIDNHostname(String s) {
    return parseHostname(s, true);
  }

  /**
   * Parses a hostname.
   *
   * @param s the string to parse
   * @return whether the hostname is valid.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1123.html#section-2">RFC 1123: 2.1 Host Names and Numbers</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1035.html#section-2.3.1>RFC 1035: 2.3.1. Preferred name syntax</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1912.html#section-2.1">RFC 1912: 2.1 Inconsistent, Missing, or Bad Data</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3696.html#section-2">RFC 3696: 2. Restrictions on domain (DNS) names</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5890.html">RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework</a>
   */
  private static boolean parseHostname(String s, boolean idn) {
    int start = 0;
    int end = s.length();

    // Allow an ending "."
    if (s.endsWith(".")) {
      end--;
    }
    if (end - start == 0 || end - start > 253) {
      return false;
    }

    while (start < end) {
      int partEnd = s.indexOf('.', start);
      if (partEnd < 0 || end <= partEnd) {
        // Last part
        partEnd = end;
      }

      if (!idn) {
        if (!parseLabel(s, start, partEnd)) {
          return false;
        }
      } else {
        if (!parseIDNLabel(s, start, partEnd)) {
          return false;
        }
      }

      // The last label can't be all digits
      if (partEnd == end) {
        if (check(s, start, end, DIGIT)) {
          return false;
        }
      }

      start = partEnd + 1;
    }

    return true;
  }

  /**
   * Checks that all the characters in the string match the characters
   * in {@code chars}.
   *
   * @param s the string to check
   * @param start the starting index
   * @param end the ending index
   * @param chars the check characters
   * @return whether the check passed.
   */
  private static boolean check(String s, int start, int end, BitSet chars) {
    for ( ; start < end; start++) {
      char c = s.charAt(start);
      if (c < 128) {
        if (!chars.get(c)) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks a regular hostname label. The rules:
   * <ul>
   * <li>Length must be between 1 and 63 characters</li>
   * <li>Cannot start or end with a dash ("-")</li>
   * <li>All characters must be ALPHA / DIGIT / "-"</li>
   * </ul>
   *
   * @param s the string to check
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @return whether the string is a valid label.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc1123.html#section-2">RFC: 1123: 2.1 Host Names and Numbers</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3696.html#section-2">RFC 3696: 2. Restrictions on domain (DNS) names</a>
   */
  public static boolean parseLabel(String s, int start, int end) {
    // ^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$

    if (end - start < 1 || 63 < end - start) {
      return false;
    }
    if (s.charAt(start) == '-' || s.charAt(end - 1) == '-') {
      return false;
    }
    return check(s, start, end, HOSTNAME_LABEL);
  }

  /**
   * Checks if a string is a valid IDN label.
   *
   * @param s the string to check
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @return whether the string is a valid IDN label.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5890.html">RFC 5890: Internationalized Domain Names for Applications (IDNA): Definitions and Document Framework</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5891.html">RFC 5891: Internationalized Domain Names in Applications (IDNA): Protocol</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5892.html">RFC 5892: The Unicode Code Points and Internationalized Domain Names for Applications (IDNA)</a>
   */
  public static boolean parseIDNLabel(String s, int start, int end) {
    // [4.2.3. Label Validation](https://www.rfc-editor.org/rfc/rfc5891.html#section-4.2.3)

    // A-labels
    // 4.2.3.1. Hyphen Restrictions
    if (s.startsWith("--", start + 2)) {
      // Reserved LDH label
      if (s.startsWith("xn--", start)) {
        // XN-label
        s = s.substring(start, end);

        // Test for valid A-label
        return !IDN.toUnicode(s).equals(s);
      }
      return false;
    }

    // Check for possible U-label
    boolean uLabel = false;
    for (int i = start; i < end; i++) {
      if (s.charAt(i) >= 128) {
        uLabel = true;
        break;
      }
    }

    if (!uLabel) {
      return parseLabel(s, start, end);
    }

    // 4.2.3.1. Hyphen Restrictions
    if (s.charAt(start) == '-' || s.charAt(end - 1) == '-') {
      return false;
    }

    // 4.2.3.2. Leading Combining Marks
    // See https://www.compart.com/en/unicode/category for examples
    int c = s.codePointAt(start);
    switch (Character.getType(c)) {
      case Character.COMBINING_SPACING_MARK:
      case Character.NON_SPACING_MARK:
      case Character.ENCLOSING_MARK:
        return false;
    }

    // Ensure the encoded length doesn't exceed 63 bytes
    try {
      if (IDN.toASCII(s.substring(start, end)).length() > 63) {
        return false;
      }
    } catch (IllegalArgumentException ex) {
      return false;
    }

    // 4.2.3.3. Contextual Rules
    boolean validKMD = false;  // valid: Appendix A.7. KATAKANA MIDDLE DOT
    boolean validAID = false;  // Appendix A.8. ARABIC-INDIC DIGITS
    boolean validEAID = false;  // Appendix A.9. EXTENDED ARABIC-INDIC DIGITS
    int charCount;
    for (int i = start; i < end; i += charCount) {
      c = s.codePointAt(i);
      charCount = Character.charCount(c);
      if (i + charCount > end) {
        return false;
      }

      switch (c) {
        // Exceptions (PVALID)
        case '\u00DF': case '\u03C2': case '\u06FD': case '\u06FE':
        case '\u0F0B': case '\u3007':
          continue;

        // Exceptions (DISALLOWED)
        case '\u0640': case '\u07FA': case '\u302E': case '\u302F':
        case '\u3031': case '\u3032': case '\u3033': case '\u3034':
        case '\u3035': case '\u303B':
          return false;

        // Exceptions (CONTEXTO)
        case '\u00B7':  // Appendix A.3. MIDDLE DOT
          if (start < i && i < (end - 1)) {
            if ((s.charAt(i - 1) == '\u006C') && (s.charAt(i + 1) == '\u006C')) {
              continue;
            }
          }
          return false;
        case '\u0375':  // Appendix A.4. GREEK LOWER NUMERAL SIGN (KERAIA)
          if (i + 1 < end) {
            int cp = s.codePointAt(i + 1);
            if (i + Character.charCount(cp) <= end) {
              if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.GREEK) {
                continue;
              }
            }
          }
          return false;
        case '\u05F3':  // Appendix A.5. HEBREW PUNCTUATION GERESH
        case '\u05F4':  // Appendix A.6. HEBREW PUNCTUATION GERSHAYIM
          if (i >= start + Character.charCount(s.codePointAt(start))) {
            if (Character.UnicodeScript.of(s.codePointBefore(i)) ==
                Character.UnicodeScript.HEBREW) {
              continue;
            }
          }
          return false;
        case '\u30FB':  // Appendix A.7. KATAKANA MIDDLE DOT
          if (!validKMD) {
            int index = start;
            while (index < end && !validKMD) {
              int cp = s.codePointAt(index);
              index += Character.charCount(cp);
              if (index > end) {
                return false;
              }

              Character.UnicodeScript script = Character.UnicodeScript.of(cp);
              switch (script) {
                case HIRAGANA:
                case KATAKANA:
                case HAN:
                  validKMD = true;
                  break;
              }
            }
            if (!validKMD) {
              return false;
            }
          }
          break;
        case '\u0660': case '\u0661': case '\u0662': case '\u0663': case '\u0664':
        case '\u0665': case '\u0666': case '\u0667': case '\u0668': case '\u0669':
          // Appendix A.8. ARABIC-INDIC DIGITS
          if (!validAID) {
            validAID = true;
            for (int j = start; j < end; j++) {
              char ch = s.charAt(j);
              if ('\u06F0' <= ch && ch <= '\u06F9') {
                validAID = false;
                break;
              }
            }
            if (!validAID) {
              return false;
            }
          }
          break;

        case '\u06F0': case '\u06F1': case '\u06F2': case '\u06F3': case '\u06F4':
        case '\u06F5': case '\u06F6': case '\u06F7': case '\u06F8': case '\u06F9':
          // Appendix A.9. EXTENDED ARABIC-INDIC DIGITS
          if (!validEAID) {
            validEAID = true;
            for (int j = start; j < end; j++) {
              char ch = s.charAt(j);
              if ('\u0660' <= ch && ch <= '\u0669') {
                validEAID = false;
                break;
              }
            }
            if (!validEAID) {
              return false;
            }
          }
          break;

        // CONTEXTJ
        case '\u200C':  // Appendix A.1. ZERO WIDTH NON-JOINER
        case '\u200D':  // Appendix A.2. ZERO WIDTH JOINER
          if (i < start + Character.charCount(s.codePointAt(start))) {
            return false;
          }

          // http://www.unicode.org/reports/tr44/#Canonical_Combining_Class_Values
          // Virama = 9
          int cp = s.codePointBefore(i);
          if (UCharacter.getCombiningClass(cp) == 9) {
            continue;
          }

          if (c == '\u200D') {
            return false;
          }

          int index = i - Character.charCount(cp);

          // RegExpMatch((Joining_Type:{L,D})(Joining_Type:T)*\u200C(Joining_Type:T)*(Joining_Type:{R,D}))
          boolean found = false;
          while (index >= start && !found) {
            switch(UCharacter.getIntPropertyValue(cp, UProperty.JOINING_TYPE)) {
              case UCharacter.JoiningType.TRANSPARENT:
                // Iterate backwards
                cp = s.codePointBefore(index);
                index -= Character.charCount(cp);
                break;
              case UCharacter.JoiningType.LEFT_JOINING:
              case UCharacter.JoiningType.DUAL_JOINING:
                found = true;
                break;
              default:
                return false;
            }
          }
          if (!found) {
            return false;
          }

          index = i + charCount;
          found = false;
          while (index < end && !found) {
            cp = s.codePointAt(index);
            index += Character.charCount(cp);
            if (index > end) {
              return false;
            }

            switch (UCharacter.getIntPropertyValue(cp, UProperty.JOINING_TYPE)) {
              case UCharacter.JoiningType.TRANSPARENT:
                break;
              case UCharacter.JoiningType.RIGHT_JOINING:
              case UCharacter.JoiningType.DUAL_JOINING:
                found = true;
                break;
              default:
                return false;
            }
          }
          if (!found) {
            return false;
          }
          break;

        default:
          if (c < 128 && HOSTNAME_LABEL.get(c)) {
            continue;
          }

          switch (Character.getType(c)) {
            case Character.LOWERCASE_LETTER:
            case Character.UPPERCASE_LETTER:
            case Character.OTHER_LETTER:
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.MODIFIER_LETTER:
            case Character.NON_SPACING_MARK:
            case Character.COMBINING_SPACING_MARK:
              break;
            case Character.UNASSIGNED:
              return false;
            default:
              return false;
          }
      }
    }

    // 4.2.3.4. Labels Containing Characters Written Right to Left
    return checkBidi(s, start, end);
  }

  private static final int MASK_R_AL_AN =
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT) |
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) |
      (1 << Character.DIRECTIONALITY_ARABIC_NUMBER);

  private static final int MASK_L_R_AL =
      (1 << Character.DIRECTIONALITY_LEFT_TO_RIGHT) |
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT) |
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC);

  private static final int MASK_L =
      (1 << Character.DIRECTIONALITY_LEFT_TO_RIGHT);

  private static final int MASK_R_AL_EN_AN =
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT) |
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER) |
      (1 << Character.DIRECTIONALITY_ARABIC_NUMBER);

  private static final int MASK_L_EN =
      (1 << Character.DIRECTIONALITY_LEFT_TO_RIGHT) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER);

  private static final int MASK_R_AL_AN_EN_ES_CS_ET_ON_BN_NSM =
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT) |
      (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) |
      (1 << Character.DIRECTIONALITY_ARABIC_NUMBER) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR) |
      (1 << Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR) |
      (1 << Character.DIRECTIONALITY_OTHER_NEUTRALS) |
      (1 << Character.DIRECTIONALITY_BOUNDARY_NEUTRAL) |
      (1 << Character.DIRECTIONALITY_NONSPACING_MARK);

  private static final int MASK_EN_AN =
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER) |
      (1 << Character.DIRECTIONALITY_ARABIC_NUMBER);

  private static final int MASK_L_EN_ES_CS_ET_ON_BN_NSM =
      (1 << Character.DIRECTIONALITY_LEFT_TO_RIGHT) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR) |
      (1 << Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR) |
      (1 << Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR) |
      (1 << Character.DIRECTIONALITY_OTHER_NEUTRALS) |
      (1 << Character.DIRECTIONALITY_BOUNDARY_NEUTRAL) |
      (1 << Character.DIRECTIONALITY_NONSPACING_MARK);

  /**
   * Checks if a string meets the Bidi criteria.
   *
   * @param s the string to check
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @return whether the string meets the criteria.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc5893.html">RFC 5893: Right-to-Left Scripts for Internationalized Domain Names for Applications (IDNA)</a>
   */
  private static boolean checkBidi(String s, int start, int end) {
    // First check that the string contains any characters from an RTL script
    int charCount;
    int mask = 0;
    boolean hasUndefined = false;
    for (int i = start; i < end; i += charCount) {
      int c = s.codePointAt(i);
      charCount = Character.charCount(c);
      if (i + charCount > end) {
        if ((mask & MASK_R_AL_AN) != 0) {
          // Invalid character, which means directionality is not technically
          // known. For RTL and LTR labels, only certain directionalities are
          // allowed, and "unknown" is not allowed.
          return false;
        }
        break;
      }
      int dir = Character.getDirectionality(c);
      if (dir >= 0) {
        mask |= (1 << dir);
      } else {
        hasUndefined = true;
      }
    }

    // Test for the presence of RTL characters
    if ((mask & MASK_R_AL_AN) == 0) {
      return true;
    }

    int dir = Character.getDirectionality(s.codePointAt(start));
    int firstMask = 0;
    if (dir >= 0) {
      firstMask |= (1 << dir);
    }

    // 1. The first character must be a character with Bidi property L, R,
    //    or AL.  If it has the R or AL property, it is an RTL label; if it
    //    has the L property, it is an LTR label.
    if ((firstMask & MASK_L_R_AL) == 0) {
      return false;
    }

    // Get directionality of last non-NSM character
    int lastMask = 0;
    int index = end;
    while (index > start) {
      int c = s.codePointBefore(index);
      index -= Character.charCount(c);
      // Validitity of all the code points was already checked so no need to
      // check if the index is < start
      dir = Character.getDirectionality(c);
      if (dir != Character.DIRECTIONALITY_NONSPACING_MARK) {
        if (dir >= 0) {
          lastMask |= (1 << dir);
        }
        break;
      }
    }

    if ((firstMask & MASK_L) == 0) {
      // 3. In an RTL label, the end of the label must be a character with
      //    Bidi property R, AL, EN, or AN, followed by zero or more
      //    characters with Bidi property NSM.
      if ((lastMask & MASK_R_AL_EN_AN) == 0) {
        return false;
      }

      // 2. In an RTL label, only characters with the Bidi properties R, AL,
      //    AN, EN, ES, CS, ET, ON, BN, or NSM are allowed.
      if (hasUndefined || (mask & ~MASK_R_AL_AN_EN_ES_CS_ET_ON_BN_NSM) != 0) {
        return false;
      }

      // 4. In an RTL label, if an EN is present, no AN may be present, and
      //    vice versa.
      if ((mask & MASK_EN_AN) == MASK_EN_AN) {
        return false;
      }
    } else {
      // 6. In an LTR label, the end of the label must be a character with
      //    Bidi property L or EN, followed by zero or more characters with
      //    Bidi property NSM.
      if ((lastMask & MASK_L_EN) == 0) {
        return false;
      }

      // 5. In an LTR label, only characters with the Bidi properties L, EN,
      //    ES, CS, ET, ON, BN, or NSM are allowed.
      if (hasUndefined || (mask & ~MASK_L_EN_ES_CS_ET_ON_BN_NSM) != 0) {
        return false;
      }
    }

    return true;
  }
}
