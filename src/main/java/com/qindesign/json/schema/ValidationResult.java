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
 * Created by shawn on 5/12/20 5:21 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * Represents a validation result. It contains a Boolean value and an optional
 * associated message. These are associated with "error"s.
 */
public final class ValidationResult {
  public final boolean result;
  public final String message;

  public ValidationResult(boolean result, String msg) {
    this.result = result;
    this.message = msg;
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, message);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ValidationResult)) {
      return false;
    }
    ValidationResult r = (ValidationResult) obj;
    return (result == r.result) && Objects.equals(message, r.message);
  }

  @Override
  public String toString() {
    if (message == null) {
      return Boolean.toString(result);
    }
    return result + ": " + message;
  }
}
