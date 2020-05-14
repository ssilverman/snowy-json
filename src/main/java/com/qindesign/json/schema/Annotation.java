/*
 * Created by shawn on 5/1/20 12:35 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;
import java.util.Objects;

/**
 * Holds all the information needed to describe an annotation.
 */
public final class Annotation {
  public final String name;
  public String instanceLocation;
  public String keywordLocation;
  public URI absKeywordLocation;
  public Object value;

  Annotation(String name) {
    this.name = name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, instanceLocation, keywordLocation, absKeywordLocation, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Annotation)) {
      return false;
    }
    Annotation a = (Annotation) obj;
    return this.equals(a) && Objects.equals(name, a.name) &&
           Objects.equals(instanceLocation, a.instanceLocation) &&
           Objects.equals(keywordLocation, a.keywordLocation) &&
           Objects.equals(absKeywordLocation, a.absKeywordLocation) &&
           Objects.equals(value, a.value);
  }

  @Override
  public String toString() {
    return name;
  }
}
