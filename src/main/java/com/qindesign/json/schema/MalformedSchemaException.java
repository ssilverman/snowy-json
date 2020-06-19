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
 * Created by shawn on 4/29/20 12:41 AM.
 */
package com.qindesign.json.schema;

import java.net.URI;

/**
 * Indicates a malformed schema. The exception contains the absolute location
 * where the error occurred.
 */
public class MalformedSchemaException extends Exception {
  private final URI location;

  public MalformedSchemaException(String message, URI location) {
    super(message);
    this.location = location;
  }

  public MalformedSchemaException(URI location) {
    super();
    this.location = location;
  }

  /**
   * Returns the absolute location in the schema where the error occurred.
   *
   * @return the absolute schema error location.
   */
  public URI getLocation() {
    return location;
  }

  @Override
  public String getMessage() {
    return location + ": " + super.getMessage();
  }
}
