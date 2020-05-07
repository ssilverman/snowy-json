/*
 * Created by shawn on 4/28/20 11:39 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;
import java.util.logging.Logger;

/**
 * Handles one schema keyword.
 */
public abstract class Keyword {
  protected final Logger logger = Logger.getLogger(getClass().getName());

  private final String name;

  /**
   * Subclasses call this with the keyword name. Names must be unique.
   *
   * @param name the keyword name
   */
  protected Keyword(String name) {
    this.name = name;
  }

  /**
   * Returns the keyword name.
   */
  public final String name() {
    return name;
  }

  /**
   * Applies the keyword.
   *
   * @param value the keyword's value
   * @param instance the instance to which the keyword is applied
   * @param context the current context
   * @return whether the keyword application was a success.
   */
  protected abstract boolean apply(JsonElement value, JsonElement instance,
                                   ValidatorContext context)
      throws MalformedSchemaException;

  @Override
  public final String toString() {
    return name;
  }
}
