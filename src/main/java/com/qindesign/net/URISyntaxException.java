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
 * Created by shawn on 6/11/20 10:35 PM.
 */
package com.qindesign.net;

import java.util.Objects;

/**
 * Exception that indicates there was an error parsing a URI or its components.
 *
 * @see URI
 */
public class URISyntaxException extends Exception {
  private final String input;
  private final int index;

  /**
   * Creates a new exception.
   *
   * @param input the URI input string
   * @param reason the failure reason
   * @param index the index of the failure, or -1 if not known
   * @throws NullPointerException if the input or reason is {@code null}.
   * @throws IllegalArgumentException if the index is &lt; -1.
   */
  public URISyntaxException(String input, String reason, int index) {
    super(reason);

    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(reason, "reason");
    if (index < -1) {
      throw new IllegalArgumentException("index < -1");
    }
    this.input = input;
    this.index = index;
  }

  /**
   * Returns the input string.
   *
   * @return the input string.
   */
  public String getInput() {
    return input;
  }

  /**
   * Returns the reason for the error.
   *
   * @return the reason for the error.
   */
  public String getReason() {
    return super.getMessage();
  }

  /**
   * Returns the parse error index, or -1 if not known.
   *
   * @return the parse error index, or -1 if not known.
   */
  public int getIndex() {
    return index;
  }

  /**
   * Returns an appropriate message for this error.
   */
  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(getReason());
    if (index >= 0) {
      sb.append(" at index ").append(index);
    }
    sb.append(": ").append(input);
    return sb.toString();
  }
}
