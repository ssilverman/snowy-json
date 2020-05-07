/*
 * Created by shawn on 4/29/20 12:41 AM.
 */
package com.qindesign.json.schema;

import java.net.URI;

/**
 * Indicates a malformed schema. The exception contains the absolute location
 * where the error occurred.
 */
public class MalformedSchemaException extends Exception {
  private final URI location;

  public MalformedSchemaException(String message, URI location) {
    super(message);
    this.location = location;
  }

  public MalformedSchemaException(URI location) {
    super();
    this.location = location;
  }

  /**
   * Returns the absolute location in the schema where the error occurred.
   */
  public URI getLocation() {
    return location;
  }

  @Override
  public String getMessage() {
    return location + ": " + super.getMessage();
  }
}
