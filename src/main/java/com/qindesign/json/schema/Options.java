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
 * There are also non-specification specific options, such as which
 * specification the validator is following.
 */
public final class Options {
  static final Map<Specification, Map<Option, Object>> specDefaults = new HashMap<>();
  static final Map<Option, Object> defaults = new HashMap<>();

  private final Map<Option, Object> options = new HashMap<>();

  static {
    specDefaults.put(Specification.DRAFT_07, new HashMap<>());
    specDefaults.put(Specification.DRAFT_2019_09, new HashMap<>());

    specDefaults.get(Specification.DRAFT_07).put(Option.FORMAT, true);
    specDefaults.get(Specification.DRAFT_2019_09).put(Option.FORMAT, false);

    defaults.put(Option.SPECIFICATION, Specification.DRAFT_2019_09);
  }

  Options() {
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
   * Gets the specified option. If there is nothing set for the option then this
   * returns {@code null}. Note that this consults the non-specification
   * defaults if the option has not been set. To get the option or the default
   * for a specific specification, see {@link #getOrDefault}.
   *
   * @param option the option to retrieve
   * @return the option, or {@code null} if not set.
   * @see #getOrDefault(Option, Specification)
   */
  public Object get(Option option) {
    Objects.requireNonNull(option, "option");

    return options.getOrDefault(option, defaults.get(option));
  }

  /**
   * Returns the value of the specified option or, if it's not set, the default
   * for the given specification. This may return {@code null} if nothing
   * is set.
   *
   * @param option the option to retrieve
   * @param spec the specification for which to get the default
   * @return the option's value or default, or {@code null}.
   */
  public Object getOrDefault(Option option, Specification spec) {
    Objects.requireNonNull(option, "option");
    Objects.requireNonNull(spec, "spec");

    return options
        .getOrDefault(option,
                      specDefaults.getOrDefault(spec, Collections.emptyMap()).get(option));
  }
}
