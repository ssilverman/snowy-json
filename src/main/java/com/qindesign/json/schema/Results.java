/*
 * Snow, a JSON Schema validator
 * Copyright (c) 2020-2021  Shawn Silverman
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
 * Created by shawn on 9/5/20 12:33 PM.
 */
package com.qindesign.json.schema;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * I'm still not completely happy with the design and structure of the
 * annotations and errors. This class, in the meantime, provides some tools for
 * post-processing them to help provide useful results.
 */
public final class Results {
  /**
   * Disallow instantiation.
   */
  private Results() {
  }

  /**
   * Processes errors from a
   * {@link Validator#validate(JsonElement, Map, Map) validation}.
   * <p>
   * This does these things:
   * <ol>
   * <li>Sorts by instance location and then by keyword location</li>
   * <li>Removes all {@link Error#isPruned() pruned} errors</li>
   * <li>Removes all {@link Error#result passing} errors</li>
   * <li>Further removes all parent errors and leaves only the children.
   *     Specifically, for each error, all other errors whose instance and
   *     keyword locations are parents of the error are removed.</li>
   * </ol>
   *
   * @param errors the error results
   * @return a list of useful errors.
   * @see Validator#validate(JsonElement, Map, Map)
   * @see Error#isPruned()
   */
  public static List<Error<?>> processErrors(Map<JSONPath, Map<JSONPath, Error<?>>> errors) {
    List<Error<?>> list = new ArrayList<>();
    errors.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          // Only add failed messages for valid failures
          e.getValue().values().stream()
              .filter(err -> !err.isPruned() && !err.result)
              .sorted(Comparator.comparing(err -> err.loc.schema))
              .forEach(list::add);
        });

    // Remove all parent errors; this makes sifting through them easier
    for (var iter = list.listIterator(); iter.hasNext(); ) {
      int i = iter.nextIndex();
      Error<?> err = iter.next();

      // The list is already sorted by instance and then by keyword, so we only
      // need to compare in one direction
      if (list.subList(i + 1, list.size()).stream()
          .anyMatch(err2 -> err2.loc.instance.startsWith(err.loc.instance) &&
                            err2.loc.schema.startsWith(err.loc.schema))) {
        iter.remove();
      }
    }

    return list;
  }

  /**
   * Processes annotations from a
   * {@link Validator#validate(JsonElement, Map, Map) validation}.
   * <p>
   * This does these things:
   * <ol>
   * <li>Sorts by instance location, then by annotation name, and then by
   *     keyword location</li>
   * <li>Removes all {@link Annotation#isValid() invalid} annotations</li>
   * </ol>
   *
   * @param annotations the error results
   * @return a list of useful annotations.
   * @see Validator#validate(JsonElement, Map, Map)
   * @see Annotation#isValid()
   */
  public static List<Annotation<?>> processAnnotations(
      Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations) {
    List<Annotation<?>> list = new ArrayList<>();
    annotations.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(byInstanceLoc -> {
          byInstanceLoc.getValue().entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(bySchemaLoc -> {
                bySchemaLoc.getValue().values().stream()
                    .filter(Annotation::isValid)
                    .sorted(Comparator.comparing(a -> a.loc.schema))
                    .forEach(list::add);
              });
        });
    return list;
  }
}
