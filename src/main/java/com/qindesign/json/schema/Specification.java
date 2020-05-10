/*
 * Created by shawn on 5/9/20 11:37 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
import java.util.Objects;

/**
 * Knows about specifications. Note that none of the URIs have a fragment, even
 * a non-empty one.
 */
public enum Specification {
  DRAFT_06("http://json-schema.org/draft-06/schema"),
  DRAFT_07("http://json-schema.org/draft-07/schema"),
  DRAFT_2019_09("https://json-schema.org/draft/2019-09/schema")
  ;

  private URI id;

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
