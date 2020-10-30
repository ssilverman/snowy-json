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
 * Created by shawn on 5/14/20 6:51 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.json.schema.net.URI;
import com.qindesign.json.schema.net.URISyntaxException;

/**
 * Convenience methods for working with URIs.
 */
public final class URIs {
  /**
   * Disallow instantiation.
   */
  private URIs() {
  }

  /**
   * Checks if the given URI has a non-empty fragment.
   *
   * @param uri the URI to check
   * @return whether the URI has a non-empty fragment.
   */
  public static boolean hasNonEmptyFragment(URI uri) {
    return (uri.rawFragment() != null) && !uri.rawFragment().isEmpty();
  }

  /**
   * Checks if the given URI does not just consist of a fragment.
   *
   * @param uri the URI to check
   * @return whether the URI is composed of a fragment only.
   */
  public static boolean isNotFragmentOnly(URI uri) {
    return uri.scheme() != null ||
           uri.rawAuthority() != null ||
           !uri.rawPath().isEmpty() ||
           uri.rawQuery() != null;
  }

  /**
   * Strips any fragment off the given URI. If there is no fragment, even an
   * empty one, then this returns the original URI.
   *
   * @param uri the URI
   * @return a fragment-stripped URI.
   * @throws IllegalArgumentException if there was an unexpected error creating
   *         the new URI. This shouldn't normally need to be caught.
   */
  public static URI stripFragment(URI uri) {
    if (uri.rawFragment() == null) {
      return uri;
    }
    try {
      return new URI(uri.scheme(), uri.authority(), uri.path(), uri.query(), null);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Unexpected bad URI: " + uri, ex);
    }
  }
}
