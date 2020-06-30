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
 * Holds all the information needed to describe an annotation. An annotation can
 * also be marked as "valid" or "invalid". The default is "invalid".
 */
public final class Annotation {
  public final String name;
  public final JSONPath instanceLocation;
  public final JSONPath keywordLocation;
  public final URI absKeywordLocation;
  public final Object value;

  private boolean valid;  // Invalid annotations are attached to failed schemas
  // NOTE: This field is not used in equals and hashCode

  Annotation(String name,
             JSONPath instanceLoc,
             JSONPath keywordLoc,
             URI absKeywordLoc,
             Object value) {
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
  public void setValid(boolean flag) {
    this.valid = flag;
  }

  /**
   * Returns whether this annotation is valid. The default value
   * is {@code false}.
   *
   * @return if this annotation is valid.
   */
  public boolean isValid() {
    return valid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, instanceLocation, keywordLocation, absKeywordLocation, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Annotation)) {
      return false;
    }
    Annotation a = (Annotation) obj;
    return Objects.equals(name, a.name) &&
           Objects.equals(instanceLocation, a.instanceLocation) &&
           Objects.equals(keywordLocation, a.keywordLocation) &&
           Objects.equals(absKeywordLocation, a.absKeywordLocation) &&
           Objects.equals(value, a.value);
  }

  @Override
  public String toString() {
    return name;
  }
}
