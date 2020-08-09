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
 * Created by shawn on 5/1/20 12:35 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.net.URI;
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
 * The default for a new annotation is "invalid". Note that the concept of
 * "annotation validity" is not related to the concept of a "validation result".
 * See {@link Error}, a special subclass of {@link Annotation} that represents a
 * validation result.
 *
 * @param <T> the type of the annotation's value
 */
public class Annotation<T> {
  public final String name;
  public final JSONPath instanceLocation;
  public final JSONPath keywordLocation;
  public final URI absKeywordLocation;
  public final T value;

  /** Invalid annotations are attached to failed schemas. */
  private boolean valid;

  Annotation(String name, JSONPath instanceLoc, JSONPath keywordLoc, URI absKeywordLoc, T value) {
    this.name = name;
    this.instanceLocation = instanceLoc;
    this.keywordLocation = keywordLoc;
    this.absKeywordLocation = absKeywordLoc;
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
  public final int hashCode() {
    return Objects.hash(name, instanceLocation, keywordLocation, absKeywordLocation, value, valid);
  }

  @Override
  public final boolean equals(Object obj) {
    if (!(obj instanceof Annotation)) {
      return false;
    }
    var a = (Annotation<?>) obj;
    return Objects.equals(name, a.name) &&
           Objects.equals(instanceLocation, a.instanceLocation) &&
           Objects.equals(keywordLocation, a.keywordLocation) &&
           Objects.equals(absKeywordLocation, a.absKeywordLocation) &&
           Objects.equals(value, a.value) &&
           valid == a.valid;
  }

  @Override
  public final String toString() {
    return name;
  }
}
