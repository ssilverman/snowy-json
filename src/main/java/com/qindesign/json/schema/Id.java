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
 * Created by shawn on 5/5/20 6:14 PM.
 */
package com.qindesign.json.schema;

import com.qindesign.net.URI;
import java.util.Objects;

/**
 * Represents a schema ID. This contains information about how it was
 * constructed, including the resource and ID of the containing document.
 * <p>
 * This class is considered to be represented by the {@link #id} field. All
 * other fields are merely auxiliary.
 */
public final class Id {
  /**
   * The actual ID, after it was resolved against the base URI. If this
   * contains a fragment then the ID was constructed from an anchor, a
   * plain name.
   * <p>
   * This field is used for all comparisons and hashing. The other fields are
   * just auxiliary.
   */
  public final URI id;

  /** The element value, may be {@code null}. */
  public final String value;

  /**
   * The base URI, against which the value was resolved to produce the ID, may
   * be {@code null}.
   */
  public final URI base;

  /** The path to this element. */
  public final JSONPath path;

  /** The root ID, may or may not be the same as the root URI. */
  public final URI rootID;

  /**
   * The root URI, the resource originally used to retrieve or describe the
   * containing JSON document.
   */
  public final URI rootURI;

  /**
   * Creates a new ID with all-null fields.
   *
   * @param id the ID, a {@link URI}
   * @throws NullPointerException if the ID is {@code null}.
   */
  public Id(URI id, String value, URI base, JSONPath path, URI rootID, URI rootURI) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(rootID, "rootID");
    Objects.requireNonNull(rootURI, "rootURI");
    this.id = id;
    this.value = value;
    this.base = base;
    this.path = path;
    this.rootID = rootID;
    this.rootURI = rootURI;
  }

  /**
   * Tests if this ID was built from an anchor. Anchors will result in an ID
   * having a non-empty fragment.
   *
   * @return the test result.
   */
  public boolean isAnchor() {
    return URIs.hasNonEmptyFragment(id);
  }

  /**
   * Returns the hash code given by {@link #id}.
   */
  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Tests whether {@link #id} equals the given object.
   *
   * @return the test result.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Id)) {
      return false;
    }
    return id.equals(((Id) obj).id);
  }

  @Override
  public String toString() {
    return id.toString();
  }
}
