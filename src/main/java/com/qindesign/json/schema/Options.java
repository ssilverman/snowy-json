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
 * Created by shawn on 5/9/20 11:32 PM.
 */
package com.qindesign.json.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles options and their defaults. This is specification-aware.
 * <p>
 * There are both specification-specific and non-specification-specific options.
 * Which is which is documented in the {@link Option} enum.
 * Specification-specific options have per-specification defaults, and can be
 * retrieved via {@link #getForSpecification(Option, Specification)}.
 * <p>
 * The specification-specific options may also have a non-specification-specific
 * default. If {@link #getForSpecification(Option, Specification)} returns
 * {@code null} then {@link #get(Option)} can be used to find that default.
 */
public final class Options {
  private static final Map<Specification, Map<Option, Object>> specDefaults = new HashMap<>();
  private static final Map<Option, Object> defaults = new HashMap<>();

  private final Map<Option, Object> options = new HashMap<>();

  static {
    specDefaults.put(Specification.DRAFT_07, new HashMap<>());
    specDefaults.put(Specification.DRAFT_2019_09, new HashMap<>());

    specDefaults.get(Specification.DRAFT_07).put(Option.FORMAT, true);
    specDefaults.get(Specification.DRAFT_2019_09).put(Option.FORMAT, false);

    defaults.put(Option.FORMAT, false);
    defaults.put(Option.DEFAULT_SPECIFICATION, Specification.DRAFT_2019_09);
    // NOTE: Don't set SPECIFICATION automatically
    defaults.put(Option.COLLECT_ANNOTATIONS, true);
    defaults.put(Option.COLLECT_ANNOTATIONS_FOR_FAILED, false);
    defaults.put(Option.COLLECT_ERRORS, true);
    defaults.put(Option.CONTENT, false);
  }

  /**
   * Creates a new Options object.
   */
  public Options() {
  }

  /**
   * Sets an option to the specified value. This overrides any defaults.
   * <p>
   * The value must be of the correct type.
   *
   * @param option the option to set
   * @param value the value to use for the option, must be of the correct type
   * @throws IllegalArgumentException if the value is not of the correct type.
   * @see Option#type()
   * @see #clear(Option)
   */
  public void set(Option option, Object value) {
    Objects.requireNonNull(option, "option");
    Objects.requireNonNull(value, "value");

    if (!option.type().isInstance(value)) {
      throw new IllegalArgumentException(
          "Bad value type: got=" + value.getClass() + " want=" + option.type());
    }
    options.put(option, value);
  }

  /**
   * Clears the specified option. This unsets the option so that any defaults
   * will be returned instead. This returns any previously set value, or
   * {@code null} if there was none.
   *
   * @param option the option to retrieve
   * @return any previously set value.
   */
  public Object clear(Option option) {
    Objects.requireNonNull(option, "option");

    return options.remove(option);
  }

  /**
   * Returns the value of the specified option or, if not set, the default. This
   * returns {@code null} if the option was not found.
   * <p>
   * Note that this consults the non-specification defaults if the option has
   * not been set. To first get the option or the default for a specific
   * specification before consulting the non-specification defaults, see
   * {@link #getForSpecification(Option, Specification)}.
   *
   * @param option the option to retrieve
   * @return the option, or {@code null} if not set.
   * @see #getForSpecification(Option, Specification)
   */
  public Object get(Option option) {
    Objects.requireNonNull(option, "option");

    return options.getOrDefault(option, defaults.get(option));
  }

  /**
   * Returns whether the specified option exists and equals {@code true}. Under
   * the hood, this calls {@link #get(Option)} and then compares it to
   * {@link Boolean#TRUE}. This means that all the same notes apply to
   * this method.
   *
   * @param option the option
   * @return whether the option exists and is equal to {@code true}.
   */
  public boolean is(Option option) {
    return Boolean.TRUE.equals(get(option));
  }

  /**
   * Returns the value of the specified option or, if it's not set, the default
   * for the given specification. If that's not set then this searches the
   * non-specification-specific defaults. This may return {@code null} if the
   * option was not found.
   *
   * @param option the option to retrieve
   * @param spec the specification for which to get the default
   * @return the option's value or default, or {@code null}.
   */
  public Object getForSpecification(Option option, Specification spec) {
    Objects.requireNonNull(option, "option");
    Objects.requireNonNull(spec, "spec");

    return options.getOrDefault(
        option,
        specDefaults
            .getOrDefault(spec, Collections.emptyMap())
            .getOrDefault(option, defaults.get(option)));
  }

  /**
   * Returns a copy of these options.
   *
   * @return a copy of the options.
   */
  public Options copy() {
    Options copy = new Options();
    copy.options.putAll(this.options);
    return copy;
  }
}
