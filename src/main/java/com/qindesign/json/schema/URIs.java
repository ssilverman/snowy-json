/*
 * Created by shawn on 5/14/20 6:51 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Convenience methods for working with URIs.
 */
public final class URIs {
  private URIs() {
  }

  /**
   * Checks if the given URI has a non-empty fragment.
   *
   * @param uri the URI to check
   * @return whether the URI has a non-empty fragment.
   */
  public static boolean hasNonEmptyFragment(URI uri) {
    return (uri.getRawFragment() != null) && !uri.getRawFragment().isEmpty();
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
    if (uri.getRawFragment() == null) {
      return uri;
    }
    try {
      return new URI(uri.getScheme(), uri.getRawSchemeSpecificPart(), null);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Unexpected bad URI: " + uri, ex);
    }
  }
}
