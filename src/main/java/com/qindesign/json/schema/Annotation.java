/*
 * Created by shawn on 5/1/20 12:35 PM.
 */
package com.qindesign.json.schema;

import java.net.URI;

/**
 * Holds all the information needed to describe an annotation.
 */
public class Annotation {
  public String name;
  public URI instanceLocation;
  public URI schemaLocation;
  public Object value;
}
