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
 * Holds all the information needed to describe an annotation.
 */
public final class Annotation {
  public final String name;
  public String instanceLocation;
  public String keywordLocation;
  public URI absKeywordLocation;
  public Object value;

  Annotation(String name) {
    this.name = name;
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
    return this.equals(a) && Objects.equals(name, a.name) &&
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
