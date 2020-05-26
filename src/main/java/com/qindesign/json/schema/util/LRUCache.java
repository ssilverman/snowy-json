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
 * Created by shawn on 5/18/20 12:44 AM.
 */
package com.qindesign.json.schema.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Implements an LRU cache. The values are produced using a given
 * producer function.
 * <p>
 * The most convenient method to use is {@link #access(Object)}. That is
 * considered to be the main method in this class.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @see <a href="https://medium.com/@krishankantsinghal/my-first-blog-on-medium-583159139237">How to implement LRU cache using HashMap and Doubly Linked List</a>
 * @see <a href="https://en.wikipedia.org/wiki/Cache_replacement_policies#Least_recently_used_(LRU)">Least recently used (LRU)</a>
 */
public final class LRUCache<K, V> {
  /**
   * A linked list entry.
   */
  private final class Entry {
    final K key;
    V value;
    Entry prev;
    Entry next;

    Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }
  }

  private final Map<K, Entry> map = new HashMap<>();
  private Entry head;
  private Entry tail;

  private final int maxSize;
  private final Function<K, V> producer;

  /**
   * Creates a new LRU cache that uses the given function to create values
   * from keys.
   *
   * @param maxSize the maximum size
   * @param producer a function that creates values from keys
   * @see <a href="https://www.baeldung.com/java-sneaky-throws">“Sneaky Throws” in Java</a>
   */
  public LRUCache(int maxSize, Function<K, V> producer) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize <= 0: " + maxSize);
    }
    Objects.requireNonNull(producer, "producer");

    this.maxSize = maxSize;
    this.producer = producer;
  }

  /**
   * Gets an entry from the cache. This will return {@code null} if the entry
   * does not exist. See {@link #access(Object)} if a new value should be
   * created when the entry is absent.
   *
   * @param key the key
   * @return the associated entry, or {@code null} if it doesn't exist.
   * @see #access(Object)
   */
  public V get(K key) {
    Entry e = map.get(key);
    if (e == null) {
      return null;
    }

    remove(e);
    insertAtHead(e);
    return e.value;
  }

  /**
   * Returns whether the cache contains an entry for the given key.
   *
   * @param key the key
   * @return whether the entry is present in the cache.
   */
  public boolean contains(K key) {
    return map.containsKey(key);
  }

  /**
   * Directly stores an entry in the cache. This will return any previous entry,
   * or {@code null} if there was none. Note that this may return {@code null}
   * if the entry was actually {@code null}. To determine if an entry exists,
   * see {@link #contains(Object)}.
   * <p>
   * It is more convenient to use {@link #access(Object)} because that manages
   * entry creation using the producer function.
   *
   * @param key the key
   * @param value the new entry
   * @return any previous entry, or {@code null} if there was no previous entry.
   * @see #contains(Object)
   * @see #access(Object)
   */
  public V put(K key, V value) {
    Entry e = map.get(key);
    V prevValue = null;

    if (e != null) {
      prevValue = e.value;
      e.value = value;
      remove(e);
      insertAtHead(e);
    } else {
      e = new Entry(key, value);
      if (map.size() >= maxSize) {
        map.remove(tail.key);
        remove(tail);
      }
      insertAtHead(e);
      map.put(key, e);
    }

    return prevValue;
  }

  /**
   * Accesses an entry in the cache. If one doesn't exist, it will be created
   * using the producer function and added to the cache. This will throw any
   * exception that the producer throws.
   * <p>
   * This is the most convenient method to use when an entry is needed, and is
   * considered to be the main method in this class.
   *
   * @param key the key
   * @return an entry, possibly new, from the cache
   */
  public V access(K key) {
    Entry e = map.get(key);
    V value;

    if (e != null) {
      value = e.value;
      remove(e);
      insertAtHead(e);
    } else {
      value = producer.apply(key);
      e = new Entry(key, value);
      if (map.size() >= maxSize) {
        map.remove(tail.key);
        remove(tail);
      }
      insertAtHead(e);
      map.put(key, e);
    }

    return value;
  }

  /**
   * Removes an entry from the linked list.
   *
   * @param e the entry to remove
   */
  private void remove(Entry e) {
    if (e.prev != null) {
      e.prev.next = e.next;
    } else {
      head = e.next;
    }
    if (e.next != null) {
      e.next.prev = e.prev;
    } else {
      tail = e.prev;
    }
  }

  /**
   * Inserts an entry at the head of the linked list.
   *
   * @param e the entry to insert
   */
  private void insertAtHead(Entry e) {
    e.next = head;
    e.prev = null;
    if (head != null) {
      head.prev = e;
    }
    head = e;
    if (tail == null) {
      tail = e;
    }
  }
}
