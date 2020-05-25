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

  /**
   * The default specification to follow, a {@link Specification}. This is used
   * when no other specification could be determined.
   */
  DEFAULT_SPECIFICATION(Specification.class),

  /**
   * The specification used when a schema has not declared which specification
   * it's using. This value is used instead of any heuristic determination. The
   * value is a {@link Specification}.
   */
  SPECIFICATION(Specification.class),

  /** Whether to collect annotations, a {@link Boolean}. */
  COLLECT_ANNOTATIONS(Boolean.class),

  /**
   * Whether to collect errors, including both valid and invalid results,
   * a {@link Boolean}. This option may need to be set to {@code false} if a
   * schema uses $recursiveRef.
   */
  COLLECT_ERRORS(Boolean.class),

  /** Whether to validate content, a {@link Boolean}. */
  CONTENT(Boolean.class)
  ;

  private final Class<?> type;

  Option(Class<?> type) {
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
