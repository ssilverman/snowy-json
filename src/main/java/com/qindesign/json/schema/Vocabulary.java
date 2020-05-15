/*
 * Created by shawn on 5/10/20 9:45 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
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

  private Vocabulary(String id) {
    Objects.requireNonNull(id);
    this.id = URI.create(id);
  }

  /**
   * Returns the ID as a {@link URI}.
   */
  public URI id() {
    return id;
  }
}
