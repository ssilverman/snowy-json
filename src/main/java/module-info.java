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
 * Created by shawn on 10/29/20 9:26 PM.
 */

/**
 * Defines the Snow validator API.
 */
module com.qindesign.json.schema {
  exports com.qindesign.json.schema;
  exports com.qindesign.json.schema.net;

  requires transitive com.google.gson;
  requires com.ibm.icu;
  requires io.github.classgraph;
  requires transitive java.logging;
}
