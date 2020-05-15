/*
 * Created by shawn on 5/9/20 11:57 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * Knows about all the options.
 */
public enum Option {
  /**
   * Indicates whether to treat "format" as an assertion, a {@link Boolean}.
   * This is a specification-specific option.
   */
  FORMAT(Boolean.class),

  /** Which specification to follow, a {@link Specification}. */
  SPECIFICATION(Specification.class),

  /** Whether to collect annotations, a {@link Boolean}. */
  COLLECT_ANNOTATIONS(Boolean.class),

  /**
   * Whether to collect errors, including both valid and invalid results,
   * a {@link Boolean}.
   */
  COLLECT_ERRORS(Boolean.class)
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
