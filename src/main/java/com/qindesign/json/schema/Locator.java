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
 * Created by shawn on 8/29/20 1:45 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.json.schema.net.URI;

import java.util.Objects;

/**
 * Locates an annotation or error.
 */
public final class Locator {
  public final JSONPath instance;
  public final JSONPath schema;
  public final URI absSchema;

  /**
   * Creates a new {@link Locator}.
   *
   * @param instance the instance location
   * @param schema the dynamic schema location
   * @param absSchema the absolute schema location
   * @throws NullPointerException if any of the arguments is {@code null}.
   */
  public Locator(JSONPath instance, JSONPath schema, URI absSchema) {
    Objects.requireNonNull(instance, "instance");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(absSchema, "absSchema");

    this.instance = instance;
    this.schema = schema;
    this.absSchema = absSchema;
  }

  @Override
  public int hashCode() {
    return Objects.hash(instance, schema, absSchema);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Locator)) {
      return false;
    }

    Locator loc = (Locator) obj;
    return instance.equals(loc.instance) &&
           schema.equals(loc.schema) &&
           absSchema.equals(loc.absSchema);
  }

  @Override
  public String toString() {
    return "Locator[" +
           "instance=" + instance +
           ", schema=" + schema +
           ", absSchema=" + absSchema + "]";
  }
}
