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
   * Indicates that annotations, when collected, should also be retained for
   * failed schemas. This option only has an effect when the
   * {@link #COLLECT_ANNOTATIONS} option is set to {@code true}.
   */
  COLLECT_ANNOTATIONS_FOR_FAILED(Boolean.class),

  /**
   * Whether to collect errors, including both valid and invalid results,
   * a {@link Boolean}.
   */
  COLLECT_ERRORS(Boolean.class),

  /** Whether to validate content, a {@link Boolean}. */
  CONTENT(Boolean.class),

  /**
   * Indicates that the validator should attempt auto-resolution when searching
   * for schemas or when otherwise resolving IDs. This entails adding the
   * original base URI and any root $id as known URLs during validation.
   */
  AUTO_RESOLVE(Boolean.class),
  ;

  private final Class<?> type;

  Option(Class<?> type) {
    Objects.requireNonNull(type, "type");
    this.type = type;
  }

  /**
   * Returns the type for this option, a {@link Class}.
   *
   * @return the option type, a {@link Class}.
   */
  public Class<?> type() {
    return type;
  }
}
