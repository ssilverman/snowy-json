/*
 * Created by shawn on 5/2/20 12:59 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;

/**
 * String utilities.
 */
public final class Strings {
  private Strings() {
  }

  /**
   * Converts the given string to JSON form and returns it. If there was any
   * internal error then this returns the string itself.
   *
   * @param s the string to convert
   * @return the converted string.
   */
  public static String jsonString(String s) {
    try (StringWriter sw = new StringWriter(s.length()); JsonWriter jw = new JsonWriter(sw)) {
      jw.value(s);
      jw.flush();
      return sw.toString();
    } catch (IOException ex) {
      return s;
    }
  }
}
