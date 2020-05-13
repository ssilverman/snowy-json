/*
 * Created by shawn on 5/12/20 5:21 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * Represents a validation result. It contains a Boolean value and an optional
 * associated message.
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
    return result + ":" + message;
  }
}
