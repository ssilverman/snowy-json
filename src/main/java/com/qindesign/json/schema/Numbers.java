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
 * Created by shawn on 4/30/20 8:45 PM.
 */
package com.qindesign.json.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Utilities for working with numbers.
 */
public final class Numbers {
  /**
   * Disallow instantiation.
   */
  private Numbers() {
  }

  /** Matches any string that has these floating-point-indicating characters. */
  private static final Pattern FLOAT_CHARS = Pattern.compile("[.eE]");

  /** The maximum length of a positive {@code long}. */
  private static final int MAX_LONG_LEN = Long.toString(Long.MAX_VALUE).length() - 1;

  /** The maximum hexadecimal length of a positive {@code long}. */
  private static final int MAX_LONG_HEX_LEN = Long.toString(Long.MAX_VALUE, 16).length() - 1;

  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
  private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigDecimal MAX_DOUBLE = new BigDecimal(Double.MAX_VALUE);
  private static final BigDecimal MIN_DOUBLE = new BigDecimal(Double.MIN_VALUE);

  /**
   * Finds the value of a string as a {@link BigDecimal}.
   * <p>
   * This allows numbers following the <a href="https://json5.org">JSON5</a>
   * spec in case the JSON parser allows it. For NaN and infinities, this
   * returns the appropriate {@link Double} value.
   *
   * @param s the string to parse
   * @return a {@link BigDecimal} for numbers and a {@link Double} for NaN
   *         and infinities.
   */
  public static BigDecimal valueOf(String s) {
//    switch (s) {
//      case "Infinity":
//      case "+Infinity":
//        return Double.POSITIVE_INFINITY;
//      case "-Infinity":
//        return Double.NEGATIVE_INFINITY;
//      case "NaN":
//      case "-NaN":
//      case "+NaN":
//        return Double.NaN;
//    }

//    if (FLOAT_CHARS.matcher(s).find()) {
//      return new BigDecimal(s);
//    }

    int nStart = 0;
    if (s.startsWith("+") || s.startsWith("-")) {
      nStart++;
    }

    if (s.startsWith("0x", nStart) || s.startsWith("0X", nStart)) {
      s = s.substring(0, nStart) + s.substring(nStart + 2);
      return new BigDecimal(new BigInteger(s, 16));
    } else {
      return new BigDecimal(s);
    }
  }

  /**
   * Returns an appropriate {@link Comparator} for the known number kinds, given
   * two numbers to compare.
   *
   * @param a the first number
   * @param b the second number
   * @return an appropriate {@link Comparator}.
   */
  public static Comparator<? super Number> comparator(Number a, Number b) {
    if (a instanceof BigDecimal) {
      if (b instanceof BigDecimal) {
        return Comparator.comparing(n -> (BigDecimal) n);
      }
      if (b instanceof BigInteger) {
        return (x, y) -> ((BigDecimal) x).compareTo(new BigDecimal((BigInteger) y));
      }
      if (b instanceof Long || b instanceof Integer) {
        return (x, y) -> ((BigDecimal) x).compareTo(BigDecimal.valueOf(y.longValue()));
      }
      return (x, y) -> ((BigDecimal) x).compareTo(BigDecimal.valueOf(y.doubleValue()));
    }

    if (a instanceof BigInteger) {
      if (b instanceof BigDecimal) {
        return (x, y) -> new BigDecimal((BigInteger) x).compareTo((BigDecimal) y);
      }
      if (b instanceof BigInteger) {
        return Comparator.comparing(n -> (BigInteger) n);
      }
      if (b instanceof Long || b instanceof Integer) {
        return (x, y) -> ((BigInteger) x).compareTo(BigInteger.valueOf(y.longValue()));
      }
      return (x, y) -> new BigDecimal((BigInteger) x).compareTo(BigDecimal.valueOf(y.doubleValue()));
    }

    if (a instanceof Long || a instanceof Integer) {
      if (b instanceof BigDecimal) {
        return (x, y) -> BigDecimal.valueOf(x.longValue()).compareTo((BigDecimal) y);
      }
      if (b instanceof BigInteger) {
        return (x, y) -> BigInteger.valueOf(x.longValue()).compareTo((BigInteger) y);
      }
      if (b instanceof Long || b instanceof Integer) {
        return Comparator.comparing(Number::longValue);
      }
      return (x, y) -> BigDecimal.valueOf(x.longValue()).compareTo(BigDecimal.valueOf(y.doubleValue()));
    }

    if (b instanceof BigDecimal) {
      return (x, y) -> BigDecimal.valueOf(x.doubleValue()).compareTo((BigDecimal) y);
    }
    if (b instanceof BigInteger) {
      return (x, y) -> BigDecimal.valueOf(x.doubleValue()).compareTo(new BigDecimal((BigInteger) y));
    }
    if (b instanceof Long || b instanceof Integer) {
      return (x, y) -> BigDecimal.valueOf(x.doubleValue()).compareTo(BigDecimal.valueOf(y.longValue()));
    }
    return Comparator.comparing(Number::doubleValue);
  }

  /**
   * Converts a string to an appropriate instance of {@link Number}, one of the
   * primitive types or one of the arbitrary-precision types. This assumes that
   * the string contains a valid JSON number.
   * <p>
   * This allows numbers following the <a href="https://json5.org">JSON5</a>
   * spec in case the JSON parser allows it.
   * <p>
   * This will return a {@link BigInteger} for integers and {@link BigDecimal}
   * for floating-point numbers having a non-zero fractional part. If the number
   * is infinite or NaN then this will return a {@link Double} having the
   * appropriate value.
   *
   * @param s the string to parse
   * @return an appropriately-chosen {@link Number} type representing the
   *         given string.
   * @throws NumberFormatException if the string isn't a valid number.
   */
  public static Number stringToNumber(String s) {
    switch (s) {
      case "Infinity":
      case "+Infinity":
        return Double.POSITIVE_INFINITY;
      case "-Infinity":
        return Double.NEGATIVE_INFINITY;
      case "NaN":
      case "-NaN":
      case "+NaN":
        return Double.NaN;
    }

    if (FLOAT_CHARS.matcher(s).find()) {
      // Floating-point

      BigDecimal d = new BigDecimal(s);
      if (d.signum() == 0) {
//        return 0;
        return BigInteger.ZERO;
      }

      // Return an integer if there's no fractional part
//      try {
//        return d.longValueExact();
//      } catch (ArithmeticException ex) {
//        // Ignore
//      }
      try {
        return d.toBigIntegerExact();
      } catch (ArithmeticException ex) {
        // Ignore
      }

      // Check if the range is within that representable by a double
//      BigDecimal absD = d.abs();
//      if (MIN_DOUBLE.compareTo(absD) < 1 && absD.compareTo(MAX_DOUBLE) < 1) {
//        return d.doubleValue();
//      }

      return d;
    }

    // Integer

    int sLen = s.length();
    int nStart = 0;
    if (s.startsWith("+") || s.startsWith("-")) {
      sLen--;
      nStart++;
    }

    BigInteger bigInt;

    // Parse with a shortcut if the number is known to be parsable as a long
    if (s.startsWith("0x", nStart) || s.startsWith("0X", nStart)) {
//      if (sLen <= MAX_LONG_HEX_LEN) {
//        return Long.parseLong(s, 16);
//      }
      s = s.substring(0, nStart) + s.substring(nStart + 2);
      bigInt = new BigInteger(s, 16);
    } else {
//      if (sLen <= MAX_LONG_LEN) {
//        return Long.parseLong(s);
//      }
      bigInt = new BigInteger(s);
    }

    // We may still have the longest-length number because the size checks don't
    // include the longest possible length (it's the longest length minus one)
//    if (MIN_LONG.compareTo(bigInt) < 1 && bigInt.compareTo(MAX_LONG) < 1) {
//      return bigInt.longValue();
//    }

    return bigInt;
  }
}
