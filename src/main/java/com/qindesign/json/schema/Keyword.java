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
 * Created by shawn on 4/28/20 11:39 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.logging.Logger;

/**
 * Handles one schema keyword.
 */
public abstract class Keyword {
  protected final Logger logger = Logger.getLogger(getClass().getName());

  private final String name;

  /**
   * Subclasses call this with the keyword name. Names must be unique.
   *
   * @param name the keyword name
   */
  protected Keyword(String name) {
    this.name = name;
  }

  /**
   * Returns the keyword name.
   *
   * @return the keyword name.
   */
  public final String name() {
    return name;
  }

  /**
   * Applies the keyword.
   *
   * @param value the keyword's value
   * @param instance the instance to which the keyword is applied
   * @param parent the keyword's parent object
   * @param context the current context
   * @return whether the keyword application was a success.
   * @throws MalformedSchemaException if the schema is not valid.
   */
  protected abstract boolean apply(JsonElement value, JsonElement instance, JsonObject parent,
                                   ValidatorContext context)
      throws MalformedSchemaException;

  @Override
  public final String toString() {
    return name;
  }
}
