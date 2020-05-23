/*
 * Created by shawn on 5/9/20 11:37 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
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
   * return {@code null} if the ID is unknown.
   *
   * @param id the ID for which to find the specification
   * @return the specification for the given ID, or {@code null} for an
   *         unknown ID.
   */
  public static Specification of(URI id) {
    return SPECS.get(id);
  }

  private final URI id;

  private Specification(String uri) {
    Objects.requireNonNull(uri, "uri");
    this.id = URI.create(uri);
  }

  /**
   * Returns the ID as a {@link URI}.
   */
  public URI id() {
    return id;
  }
}
