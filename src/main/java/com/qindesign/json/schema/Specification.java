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
 * Created by shawn on 5/9/20 11:37 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Knows about specifications. Note that none of the URIs have a fragment, even
 * a non-empty one.
 */
public enum Specification {
  DRAFT_06("http://json-schema.org/draft-06/schema"),
  DRAFT_07("http://json-schema.org/draft-07/schema"),
  DRAFT_2019_09("https://json-schema.org/draft/2019-09/schema")
  ;

  private static final Map<URI, Specification> SPECS = Stream.of(
      values()).collect(Collectors.toUnmodifiableMap(Specification::id, Function.identity()));

  /**
   * Returns the specification having the given ID, a {@link URI}. This will
   * return {@code null} if the ID is unknown. This does not first normalize
   * the value.
   *
   * @param id the ID for which to find the specification
   * @return the specification for the given ID, or {@code null} for an
   *         unknown ID.
   */
  public static Specification of(URI id) {
    return SPECS.get(id);
  }

  private final URI id;

  Specification(String uri) {
    Objects.requireNonNull(uri, "uri");
    this.id = URI.parseUnchecked(uri);
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
