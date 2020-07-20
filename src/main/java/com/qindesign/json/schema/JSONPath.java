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
 * Created by shawn on 6/23/20 6:19 PM.
 */
package com.qindesign.json.schema;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Represents a schema path. When converting to a string with
 * {@link #toString()}, each element will be converted to a valid JSON Pointer
 * token. When parsing a string, all the path elements will be converted from
 * JSON Pointer tokens.
 * <p>
 * This object is immutable.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
 */
public final class JSONPath extends AbstractList<String>
    implements Iterable<String>, Comparable<JSONPath> {
  private final boolean isAbsolute;
  private final List<String> list;

  /**
   * Creates a new empty absolute path.
   *
   * @return a new empty absolute path.
   */
  public static JSONPath absolute() {
    return new JSONPath(true, Collections.emptyList());
  }

  /**
   * Creates a new empty relative path.
   *
   * @return a new empty relative path.
   */
  public static JSONPath relative() {
    return new JSONPath(false, Collections.emptyList());
  }

  /**
   * Creates a new relative path and sets the first element to the given string.
   *
   * @param element the first element of the path
   * @return a new relative path having the specified first element.
   */
  public static JSONPath fromElement(String element) {
    return new JSONPath(false, List.of(element));
  }

  /**
   * Creates a new path from the given path string. The rules:
   * <ol>
   * <li>Starts with "/": absolute path, otherwise: relative path</li>
   * <li>Empty: empty relative path, otherwise: non-empty path</li>
   * <li>Path elements are separated by "/"</li>
   * </ol>
   *
   * @param s the string to parse
   * @return the new path.
   */
  public static JSONPath fromPath(String s) {
    return fromPath(s, false);
  }

  /**
   * Creates a new path from the given JSON Pointer string. The rules:
   * <ol>
   * <li>Starts with "/": absolute path, otherwise: relative path
   *     if non-empty</li>
   * <li>Empty: empty absolute path, otherwise: non-empty path</li>
   * <li>Relative non-empty strings are allowed and become relative
   *     non-empty paths</li>
   * <li>Path elements are separated by "/"</li>
   * </ol>
   * <p>
   * All the path elements will be converted from a JSON Pointer token.
   *
   * @param s the string to parse
   * @see <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
   * @return the new path.
   */
  public static JSONPath fromJSONPointer(String s) {
    return fromPath(s, true);
  }

  /**
   * Parses a path, possibly as a JSON Pointer.
   *
   * @param s the string to parse
   * @param isJSONPtr whether to treat as a JSON Pointer
   * @return the new path.
   */
  private static JSONPath fromPath(String s, boolean isJSONPtr) {
    if (s.isEmpty()) {
      return isJSONPtr ? absolute() : relative();
    }

    JSONPath p = new JSONPath(s.startsWith("/"), new ArrayList<>());

    int index = s.indexOf("/");
    if (index < 0) {
      p.addElement(s, isJSONPtr);
      return p;
    }

    if (index > 0) {
      p.addElement(s.substring(0, index), isJSONPtr);
    }
    while (true) {
      int next = s.indexOf('/', index + 1);
      if (next < 0) {
        p.addElement(s.substring(index + 1), isJSONPtr);
        break;
      }
      p.addElement(s.substring(index + 1, next), isJSONPtr);
      index = next;
    }
    return p;
  }

  /**
   * Creates a new path. The parameter indicates whether this is an
   * absolute path.
   *
   * @param isAbsolute whether the path is absolute
   * @param list the list to use
   */
  private JSONPath(boolean isAbsolute, List<String> list) {
    this.isAbsolute = isAbsolute;
    this.list = list;
  }

  /**
   * Copy constructor.
   *
   * @param p the path to copy
   */
  private JSONPath(JSONPath p) {
    this(p.isAbsolute, new ArrayList<>(p.list));
  }

  /**
   * Adds a path element and maybe converts it from a JSON Pointer token.
   *
   * @param element the path element to add
   * @param isJSONPtr whether to convert from a JSON Pointer token
   * @see Strings#fromJSONPointerToken(String)
   * @throws NullPointerException if the element is {@code null}.
   */
  private void addElement(String element, boolean isJSONPtr) {
    Objects.requireNonNull(element, "element");
    if (isJSONPtr) {
      list.add(Strings.fromJSONPointerToken(element));
    } else {
      list.add(element);
    }
  }

  /**
   * Returns whether this is an absolute path.
   *
   * @return whether this is an absolute path.
   */
  public boolean isAbsolute() {
    return isAbsolute;
  }

  /**
   * Gets the element at the specified position. Note that it will not be
   * encoded as a JSON Pointer token.
   *
   * @param index position of the element
   * @return the element at the specified position.
   */
  public String get(int index) {
    return list.get(index);
  }

  /**
   * Returns the element count.
   *
   * @return the element count.
   */
  public int size() {
    return list.size();
  }

  /**
   * Returns whether the path is empty.
   *
   * @return whether the path is empty.
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * Appends an element to the path and returns a new path.
   *
   * @param element the element to append
   * @return the new path.
   * @throws NullPointerException if the element is {@code null}.
   */
  public JSONPath append(String element) {
    Objects.requireNonNull(element, "element");
    JSONPath p = new JSONPath(isAbsolute, new ArrayList<>(list.size() + 1));
    p.list.addAll(this.list);
    p.list.add(element);
    return p;
  }

  /**
   * Removes the last element if there is one.
   */
  private void maybeRemoveLast() {
    if (!list.isEmpty()) {
      list.remove(list.size() - 1);
    }
  }

  /**
   * Returns whether this path starts with the given path. This ignores the
   * relative/absolute property.
   * <p>
   * One way to think of this is a sort of "greater than or equal to" operator.
   *
   * @param p the path to check
   * @return whether this path starts with the given path.
   */
  public boolean startsWith(JSONPath p) {
    if (size() < p.size()) {
      return false;
    }
    return list.subList(0, p.size()).equals(p.list);
  }

  /**
   * Returns whether this path ends with the given element.
   *
   * @param element the element to check
   * @return whether this path ends with the given element.
   */
  public boolean endsWith(String element) {
    return !list.isEmpty() && list.get(list.size() - 1).equals(element);
  }

  /**
   * Returns an iterator over the elements.
   *
   * @return an iterator over the elements.
   */
  @Override
  public Iterator<String> iterator() {
    return list.iterator();
  }

  /**
   * Performs path normalization, similar to how URIs in RFC 3986 do it. This
   * returns a new path object.
   *
   * @return the normalized path.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2.4">5.2.4. Remove Dot Segments</a>
   */
  public JSONPath normalize() {
    JSONPath np = new JSONPath(isAbsolute(), new ArrayList<>(size()));

    for (int i = 0; i < size(); i++) {
      String e1 = get(i);
      if (!isAbsolute() && (e1.equals("..") || e1.equals("."))) {
        // 5.2.4. Step 2.A.
      } else if (isAbsolute() && e1.equals(".")) {
        // 5.2.4. Step 2.B.
        if (i + 1 >= size()) {
          np.list.add("");
        }
      } else if (isAbsolute() && e1.equals("..")) {
        // 5.2.4. Step 2.C.
        np.maybeRemoveLast();
        if (i + 1 >= size()) {
          np.list.add("");
        }
      } else {
        // 5.2.4. Step 2.E.
        np.list.add(e1);
      }
    }
    return np;
  }

  /**
   * Performs resolution, similar to how URIs in RFC 3986 do it. This returns a
   * new path object.
   *
   * @param p the path to resolve
   * @return the resolve path.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2">5.2. Relative Resolution</a>
   */
  public JSONPath resolve(JSONPath p) {
    if (p.isAbsolute()) {
      return p.normalize();
    }
    JSONPath np = new JSONPath(this);
    if (p.list.isEmpty()) {
      return np;
    }
    if (!this.isAbsolute() || !this.list.isEmpty()) {
      np.maybeRemoveLast();
    }
    np.list.addAll(p.list);
    return np.normalize();
  }

  /**
   * Compares this path to another path. Absolute paths always compare greater
   * than relative paths.
   * <p>
   * This compares each path element using {@link String#compareTo(String)}.
   *
   * @param p the path to be compared
   * @return the comparison result, negative for less than, 0 for equal to, or
   *         positive for greater than.
   */
  @Override
  public int compareTo(JSONPath p) {
    // Absolute sorts after non-absolute
    if (this.isAbsolute() != p.isAbsolute()) {
      if (this.isAbsolute()) {
        return 1;
      } else {
        return -1;
      }
    }

    int limit = Math.min(this.size(), p.size());
    for (int i = 0; i < limit; i++) {
      int r = this.get(i).compareTo(p.get(i));
      if (r != 0) {
        return r;
      }
    }

    return this.size() - p.size();
  }

  /**
   * Returns a hash code for this path.
   *
   * @return a hash code for this path.
   */
  @Override
  public int hashCode() {
    return Objects.hash(isAbsolute, list);
  }

  /**
   * Returns whether this path is considered equal to the given object.
   *
   * @param obj the object to compare
   * @return whether this path is equal to the given object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof JSONPath)) {
      return false;
    }
    return (this.isAbsolute == ((JSONPath) obj).isAbsolute) &&
           (this.list.equals(((JSONPath) obj).list));
  }

  /**
   * Returns the path separated by "/" characters. If the path is empty, then
   * this will return an empty string, for both relative and absolute paths.
   * <p>
   * Each element will be converted to a valid JSON Pointer token.
   *
   * @return a string representation of this path.
   * @see <a href="https://tools.ietf.org/html/rfc6901">JSON Pointer</a>
   */
  @Override
  public String toString() {
    if (list.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (isAbsolute()) {
      sb.append('/');
    }
    sb.append(list.get(0));
    IntStream.range(1, list.size())
        .forEach(i -> sb.append('/').append(Strings.jsonPointerToken(list.get(i))));
    return sb.toString();
  }
}
