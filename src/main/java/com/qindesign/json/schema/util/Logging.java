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
 * Created by shawn on 5/25/20 2:41 PM.
 */
package com.qindesign.json.schema.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assists with logging. This includes formatting and setup.
 */
public final class Logging {
  // Set up the display format
  static {
    System.setProperty(
        "java.util.logging.SimpleFormatter.format",
        "%3$s: %1$tc [%4$s] %5$s%6$s%n");
  }

  /**
   * Initializes a logger to the given level. This works by modifying the parent
   * logger and all its handlers to have the given level.
   *
   * @param logger the logger
   * @param level the level
   */
  public static void init(Logger logger, Level level) {
    // Don't do it this way because that affects the global logger:
//    Logger logger = LogManager.getLogManager().getLogger("");
//    logger.setLevel(Level.CONFIG);
//    for (Handler h : logger.getHandlers()) {
//      h.setLevel(Level.CONFIG);
//    }

    Logger parentLogger = logger.getParent();
    parentLogger.setUseParentHandlers(false);
    parentLogger.setLevel(level);
    Handler[] handlers = parentLogger.getHandlers();
    if (handlers.length == 0) {
      Handler h = new ConsoleHandler();
      h.setLevel(level);
      parentLogger.addHandler(h);
    } else {
      for (Handler h : handlers) {
        h.setLevel(level);
      }
    }
  }

  /**
   * Disallow instantiation.
   */
  private Logging() {
  }
}
