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
 * Created by shawn on 8/8/20 10:11 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.net.URI;
import java.util.Objects;

/**
 * An error, a special type of annotation that represents a validation result.
 * If the result is {@code true} then the name will be "annotation", otherwise
 * the name will be "error".
 * <p>
 * The associated annotation value is an {@link Error.Value}, the validation
 * result itself. This result contains the Boolean-valued result and an
 * associated value describing that result.
 *
 * @param <T> the type of the associated value
 */
public final class Error<T> extends Annotation<Error.Value<T>> {
  /**
   * Represents a validation result. It contains a Boolean value and an optional
   * associated value. These are associated with "error"s.
   *
   * @param <T> the type of the associated value
   */
  public static final class Value<T> {
    public final boolean result;
    public final T value;

    Value(boolean result, T value) {
      this.result = result;
      this.value = value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(result, value);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Value)) {
        return false;
      }
      Value<?> r = (Value<?>) obj;
      return (result == r.result) && Objects.equals(value, r.value);
    }

    @Override
    public String toString() {
      if (value == null) {
        return Boolean.toString(result);
      }
      return result + ": " + value;
    }
  }

  /**
   * Creates a new error, a validation result.
   *
   * @param instanceLoc the instance location
   * @param keywordLoc the dynamic keyword location
   * @param absKeywordLoc the absolute keyword location
   * @param result the validation result
   * @param value a value associated with the validation result
   */
  Error(JSONPath instanceLoc, JSONPath keywordLoc, URI absKeywordLoc, boolean result, T value) {
    super(result ? "annotation" : "error",
        instanceLoc, keywordLoc, absKeywordLoc,
        new Value<>(result, value));
  }
}
