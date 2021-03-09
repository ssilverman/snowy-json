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
 * Created by shawn on 4/30/20 8:45 PM.
 */
package com.qindesign.json.schema;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utilities for working with numbers.
 */
public final class Numbers {
  /**
   * Disallow instantiation.
   */
  private Numbers() {
  }

  /**
   * Finds the value of a string as a {@link BigDecimal}.
   * <p>
   * With the exception of infinities and NaNs, this allows numbers that follow
   * the <a href="https://json5.org">JSON5</a> specification in case the JSON
   * parser allows it.
   *
   * @param s the string to parse
   * @return a {@link BigDecimal} value.
   */
  public static BigDecimal valueOf(String s) {
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
   * Tests if the given {@link BigDecimal} is an integer.
   *
   * @param n the number to test
   * @return whether the number is an integer.
   */
  public static boolean isInteger(BigDecimal n) {
    return n.scale() <= 0 || n.stripTrailingZeros().scale() <= 0;
  }
}
