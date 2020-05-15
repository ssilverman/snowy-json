/*
 * Created by shawn on 5/9/20 11:57 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * Knows about all the options.
 */
public enum Option {
  /** Indicates whether to treat "format" as an assertion, a Boolean. */
  FORMAT(Boolean.class),
  SPECIFICATION(Specification.class)
  ;

  private final Class<?> type;

  private Option(Class<?> type) {
    Objects.requireNonNull(type, "type");
    this.type = type;
  }

  /**
   * Returns the type for this option, a {@link Class}.
   */
  public Class<?> type() {
    return type;
  }
}
