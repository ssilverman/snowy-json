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
 * Created by shawn on 5/10/20 9:45 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.json.schema.net.URI;

import java.util.Objects;

/**
 * Knows about all the vocabularies.
 */
public enum Vocabulary {
  CORE("https://json-schema.org/draft/2019-09/vocab/core"),
  APPLICATOR("https://json-schema.org/draft/2019-09/vocab/applicator"),
  VALIDATION("https://json-schema.org/draft/2019-09/vocab/validation"),
  METADATA("https://json-schema.org/draft/2019-09/vocab/meta-data"),
  FORMAT("https://json-schema.org/draft/2019-09/vocab/format"),
  CONTENT("https://json-schema.org/draft/2019-09/vocab/content")
  ;

  private final URI id;

  Vocabulary(String id) {
    Objects.requireNonNull(id);
    this.id = URI.parseUnchecked(id);
  }

  /**
   * Returns the ID as a {@link URI}.
   *
   * @return the ID, a {@link URI}.
   */
  public URI id() {
    return id;
  }
}
