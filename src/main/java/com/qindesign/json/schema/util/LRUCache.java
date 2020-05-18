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
    K key;
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

  private int maxSize;
  private Function<K, V> producer;

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
   * does not exist.
   *
   * @param key the key
   * @return the associated entry, or {@code null} if it doesn't exist.
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
   * Accesses an entry in the cache. If one doesn't exist, it will be created
   * using the producer function and added to the cache. This will throw any
   * exception that the producer throws.
   *
   * @param key the key
   */
  public V access(K key) {
    Entry e = map.get(key);
    V value = producer.apply(key);

    if (e != null) {
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
