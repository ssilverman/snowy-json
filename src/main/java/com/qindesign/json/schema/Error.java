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
 * Created by shawn on 8/8/20 10:11 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * An "error", represents a validation result.
 *
 * @param <T> the type of the associated value
 */
public final class Error<T> {
  /** The validation result. */
  public final boolean result;

  /** The error location. */
  public final Locator loc;

  /** The associated value, may be {@code null}. */
  public final T value;

  private boolean pruned;

  /**
   * Creates a new error, a validation result.
   *
   * @param result the validation result
   * @param loc the locator
   * @param value a value associated with the validation result
   * @throws NullPointerException if the locator is {@code null}
   */
  Error(boolean result, Locator loc, T value) {
    Objects.requireNonNull(loc, "loc");

    this.result = result;
    this.loc = loc;
    this.value = value;
  }

  /**
   * Sets the "pruned" flag. The default value is {@code false}.
   *
   * @param flag the new "pruned" value
   */
  public final void setPruned(boolean flag) {
    this.pruned = flag;
  }

  /**
   * Returns whether this error has been pruned. The default value
   * is {@code false}.
   *
   * @return if this error has been pruned.
   */
  public final boolean isPruned() {
    return pruned;
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, loc, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Error)) {
      return false;
    }

    var e = (Error<?>) obj;
    return (result == e.result) &&
           Objects.equals(loc, e.loc) &&
           Objects.equals(value, e.value) &&
           pruned == e.pruned;
  }

  @Override
  public String toString() {
    if (value == null) {
      return Boolean.toString(result);
    }
    return result + ": " + value;
  }
}
