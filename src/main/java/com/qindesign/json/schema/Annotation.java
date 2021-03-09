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
 * Created by shawn on 5/1/20 12:35 PM.
 */
package com.qindesign.json.schema;

import java.util.Objects;

/**
 * Holds all the information needed to describe an annotation, including a name,
 * a set of locations, and an associated value.
 * <p>
 * An annotation can also be marked as "valid" or "invalid", signifying whether
 * it contributes to the annotations in a successful schema validation, or
 * whether it can be ignored. This is useful for tracing how annotations are
 * applied for unsuccessful or failed schema validations. Another way of
 * thinking about this is that "invalid" means "pruned" and "valid" means
 * "not pruned".
 * <p>
 * Note that the concept of "annotation validity" is not related to the concept
 * of a "validation result". See {@link Error} instead.
 *
 * @param <T> the type of the annotation's value
 */
public final class Annotation<T> {
  /** The annotation name. */
  public final String name;

  /** The annotation location. */
  public final Locator loc;

  /** The associated value, may be {@code null}. */
  public final T value;

  /** Invalid annotations are attached to failed schemas. */
  private boolean valid;

  /**
   * Creates a new annotation.
   *
   * @param name the annotation name
   * @param loc the locator
   * @param value the associated value
   * @throws NullPointerException if the locator is {@code null}.
   */
  Annotation(String name, Locator loc, T value) {
    Objects.requireNonNull(loc, "loc");

    this.name = name;
    this.loc = loc;
    this.value = value;
  }

  /**
   * Sets the "valid" flag. The default value is {@code false}.
   *
   * @param flag the new "valid" value
   */
  public final void setValid(boolean flag) {
    this.valid = flag;
  }

  /**
   * Returns whether this annotation is valid. The default value
   * is {@code false}.
   *
   * @return if this annotation is valid.
   */
  public final boolean isValid() {
    return valid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, loc, value, valid);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Annotation)) {
      return false;
    }

    var a = (Annotation<?>) obj;
    return Objects.equals(name, a.name) &&
           Objects.equals(loc, a.loc) &&
           Objects.equals(value, a.value) &&
           valid == a.valid;
  }

  @Override
  public String toString() {
    return name;
  }
}
