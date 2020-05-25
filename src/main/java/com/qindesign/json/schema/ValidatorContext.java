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
 * Created by shawn on 4/29/20 12:49 AM.
 */
package com.qindesign.json.schema;

import com.google.common.reflect.ClassPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.keywords.AdditionalItems;
import com.qindesign.json.schema.keywords.AdditionalProperties;
import com.qindesign.json.schema.keywords.CoreAnchor;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.CoreRecursiveAnchor;
import com.qindesign.json.schema.keywords.CoreRef;
import com.qindesign.json.schema.keywords.CoreSchema;
import com.qindesign.json.schema.keywords.CoreVocabulary;
import com.qindesign.json.schema.keywords.MaxContains;
import com.qindesign.json.schema.keywords.MinContains;
import com.qindesign.json.schema.keywords.UnevaluatedItems;
import com.qindesign.json.schema.keywords.UnevaluatedProperties;
import com.qindesign.json.schema.util.LRUCache;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The schema processing state.
 */
public final class ValidatorContext {
  private static final Class<?> CLASS = ValidatorContext.class;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  /** Represents all keywords not in a specific set. */
  private static final Set<String> EVERY_OTHER_KEYWORD = Collections.emptySet();

  /**
   * All the known keywords, and the order in which they must be processed. A
   * set's position in the list indicates the processing order.
   */
  private static final List<Set<String>> KEYWORD_SETS = List.of(
      Set.of(CoreSchema.NAME),
      Set.of(CoreId.NAME),
      Set.of(
          CoreRecursiveAnchor.NAME,
          CoreAnchor.NAME,
          CoreVocabulary.NAME),
      EVERY_OTHER_KEYWORD,
      Set.of(
          AdditionalItems.NAME,
          AdditionalProperties.NAME,
          MaxContains.NAME,
          MinContains.NAME),
      Set.of(
          UnevaluatedItems.NAME,
          UnevaluatedProperties.NAME));

  private static final Map<String, Keyword> keywords;
  private static final Map<String, Integer> keywordClasses;

  /**
   * Tracks context state.
   */
  private static final class State {
    State() {
    }

    /**
     * The current schema object, could be the parent of the current element.
     */
    JsonObject schemaObject;

    /** Indicates that the current schema object is the root schema. */
    boolean isRoot;

    /** The current base URI. This is the base URI of the closest ancestor. */
    URI baseURI;

    /** The current specification. */
    Specification spec;

    /** The previous $recursiveAnchor=true base. */
    URI prevRecursiveBaseURI;

    /** Any $recursiveAnchor=true bases we find along the way. */
    URI recursiveBaseURI;

    String keywordParentLocation;
    String keywordLocation;
    URI absKeywordLocation;
    String instanceLocation;

    /** Flag that indicates whether to collect annotations, an optimization. */
    boolean isCollectSubAnnotations;

    /**
     * Annotations local only to this schema instance and none of
     * its descendants.
     */
    final Map<String, Object> localAnnotations = new HashMap<>();

    /**
     * Copies and returns the object. This does not copy anything that needs to
     * remain "local".
     */
    State copy() {
      State copy = new State();
      copy.schemaObject = this.schemaObject;
      copy.isRoot = this.isRoot;
      copy.baseURI = this.baseURI;
      copy.spec = this.spec;
      copy.prevRecursiveBaseURI = this.prevRecursiveBaseURI;
      copy.recursiveBaseURI = this.recursiveBaseURI;
      copy.keywordParentLocation = this.keywordParentLocation;
      copy.keywordLocation = this.keywordLocation;
      copy.absKeywordLocation = this.absKeywordLocation;
      copy.instanceLocation = this.instanceLocation;
      copy.isCollectSubAnnotations = this.isCollectSubAnnotations;
      return copy;
    }
  }

  static {
    // First, load all the known keywords
    Map<String, Keyword> words;
    try {
      words = Collections.unmodifiableMap(findKeywords());
    } catch (IOException ex) {
      words = Collections.emptyMap();
      logger.log(Level.SEVERE, "Error finding keywords", ex);
    }
    keywords = words;
    // The 'keywords' set now contains all the keywords

    // Fun with streams
    // Map each keyword to its "class number", so we can sort easily
    var classes = IntStream.range(0, KEYWORD_SETS.size())
        .boxed()
        .flatMap(i -> KEYWORD_SETS.get(i).stream().map(name -> Map.entry(name, i)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    int otherClass = KEYWORD_SETS.indexOf(EVERY_OTHER_KEYWORD);
    keywords.keySet().forEach(name -> classes.putIfAbsent(name, otherClass));
    keywordClasses = Collections.unmodifiableMap(classes);
  }

  /**
   * Finds all the keyword implementations and returns a map mapping names to
   * keyword implementations.
   */
  @SuppressWarnings("UnstableApiUsage")
  private static Map<String, Keyword> findKeywords() throws IOException {
    ClassPath classPath = ClassPath.from(CLASS.getClassLoader());
    Set<ClassPath.ClassInfo> classes =
        classPath.getTopLevelClasses(CLASS.getPackage().getName() + ".keywords");

    Map<String, Keyword> keywords = new HashMap<>();

    for (ClassPath.ClassInfo classInfo : classes) {
      Class<?> c = classInfo.load();
      if (!Keyword.class.isAssignableFrom(c)) {
        continue;
      }
      try {
        Keyword keyword = (Keyword) c.getDeclaredConstructor().newInstance();
        Keyword oldKeyword = keywords.putIfAbsent(keyword.name(), keyword);
        if (oldKeyword != null) {
          logger.severe("Duplicate keyword: " + keyword.name() + ": " + c);
        } else {
          logger.fine("Keyword: " + keyword.name());
        }
      } catch (ReflectiveOperationException | RuntimeException ex) {
        logger.log(Level.SEVERE, "Error loading keyword: " + c, ex);
        Throwable cause = ex.getCause();
        if (cause != null) {
          cause.printStackTrace();
        }
      }
    }

    return keywords;
  }

  /** Vocabularies in use. */
  private final Map<URI, Boolean> vocabularies = new HashMap<>();

  /**
   * Annotations collection, maps element location to its annotations:<br>
   * instance location -> name -> schema location -> value
   */
  private final Map<String, Map<String, Map<String, Annotation>>> annotations;

  /**
   * Error "annotation" collection, maps element location to its errors:<br>
   * instance location -> schema location -> value
   */
  private final Map<String, Map<String, Annotation>> errors;

  /**
   * The initial base URI passed in with the constructor. This may or may not
   * match the document ID.
   */
  private final URI baseURI;

  /** The current processing state. */
  private State state;

  private final Map<Id, JsonElement> knownIDs;
  private final Map<URI, Id> idsByURI;
  private final Map<URI, URL> knownURLs;

  /**
   * Tracks schemas that have either been validated or in the process of being
   * validated, to avoid $schema recursion.
   * <p>
   * This being non-empty is a good indicator that this is a meta-schema.
   */
  private final Set<URI> validatedSchemas;

  // Options
  private final Options options;
  private final boolean isFailFast;
  private final boolean isCollectAnnotations;
  private final boolean isCollectErrors;

  // Pattern cache
  private static final int MAX_PATTERN_CACHE_SIZE = 20;
  private final LRUCache<String, java.util.regex.Pattern> patternCache =
      new LRUCache<>(MAX_PATTERN_CACHE_SIZE, java.util.regex.Pattern::compile);

  /**
   * Creates a new schema context. Given is an absolute URI from where the
   * schema was obtained. The URI will be normalized if it is not already.
   * <p>
   * Only empty fragments are allowed in the base URI, if present.
   * <p>
   * This directly uses the arguments and does not copy them or wrap them in
   * unmodifiable wrappers.
   *
   * @param baseURI the initial base URI
   * @param knownIDs known JSON contents
   * @param knownURLs known resources
   * @param validatedSchemas the set of validated schemas
   * @param options any options
   * @param annotations annotations get stored here
   * @param errors errors get stored here
   * @throws IllegalArgumentException if the base URI is not absolute or if it
   *         has a non-empty fragment.
   * @throws NullPointerException if any of the arguments are {@code null}.
   */
  public ValidatorContext(URI baseURI,
                          Map<Id, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                          Set<URI> validatedSchemas, Options options,
                          Map<String, Map<String, Map<String, Annotation>>> annotations,
                          Map<String, Map<String, Annotation>> errors) {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(knownIDs, "knownIDs");
    Objects.requireNonNull(knownURLs, "knownURLs");
    Objects.requireNonNull(validatedSchemas, "validatedSchemas");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(annotations, "annotations");
    Objects.requireNonNull(errors, "errors");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }
    if (URIs.hasNonEmptyFragment(baseURI)) {
      throw new IllegalArgumentException("baseURI has a non-empty fragment");
    }
    if (baseURI.getRawFragment() == null) {
      // Ensure there's an empty fragment
      try {
        baseURI = new URI(baseURI.getScheme(), baseURI.getRawSchemeSpecificPart(), "");
      } catch (URISyntaxException ex) {
        throw new IllegalArgumentException("Unexpected bad base URI");
      }
    }

    this.baseURI = baseURI.normalize();
    this.knownIDs = knownIDs;
    this.idsByURI = knownIDs.keySet().stream()
        .collect(Collectors.toMap(id -> id.id, Function.identity()));
    this.knownURLs = knownURLs;
    this.validatedSchemas = validatedSchemas;

    state = new State();
    state.baseURI = baseURI;
    state.spec = (Specification) options.get(Option.DEFAULT_SPECIFICATION);
    state.prevRecursiveBaseURI = null;
    state.recursiveBaseURI = null;
    state.schemaObject = null;
    state.isRoot = true;
    state.keywordParentLocation = null;
    state.keywordLocation = "";
    state.absKeywordLocation = baseURI;
    state.instanceLocation = "";
    state.isCollectSubAnnotations = true;

    // Options
    this.options = options.copy();
    isCollectAnnotations = isOption(Option.COLLECT_ANNOTATIONS);
    isCollectErrors = isOption(Option.COLLECT_ERRORS);
    isFailFast = !isCollectAnnotations && !isCollectErrors;

    if (isCollectAnnotations) {
      this.annotations = annotations;
    } else {
      this.annotations = null;
    }
    if (isCollectErrors) {
      this.errors = errors;
    } else {
      this.errors = null;
    }
  }

  /**
   * Returns the option value, first consulting the option for the current
   * specification, and then consulting the non-specification-specific options
   * and defaults. This may return {@code null} if the option was not found.
   * <p>
   * It is up to the caller to use a sensible default if this
   * returns {@code null}.
   * <p>
   * The following expression will return {@code false} when the option
   * is {@code false} and {@code true} when the option is {@code true} or
   * {@code null} (and will return {@code true} for any other object):
   * <pre>!Boolean.FALSE.equals(retval)</pre>
   * <p>
   * The following expression will return {@code true} when the option is
   * {@code true} and {@code false} otherwise:
   * <pre>Boolean.TRUE.equals(retval)</pre>
   * <p>
   * The difference is in the behaviour when the option is absent. The first
   * expression will default to {@code true} and the second expression will
   * default to {@code false}.
   *
   * @param opt the option to retrieve
   * @return the option value, or {@code null} if it was not found.
   */
  public Object option(Option opt) {
    return options.getForSpecification(opt, specification());
  }

  /**
   * Returns {@code true} if the option is present and equal to Boolean true,
   * and {@code false} otherwise.
   *
   * @param opt the option to test
   * @return {@code true} if the option is Boolean true.
   */
  public boolean isOption(Option opt) {
    return Boolean.TRUE.equals(option(opt));
  }

  /**
   * Returns whether we can fail fast when processing a keyword.
   */
  public boolean isFailFast() {
    return isFailFast;
  }

  /**
   * Returns the pattern cache.
   */
  public LRUCache<String, java.util.regex.Pattern> patternCache() {
    return patternCache;
  }

  /**
   * Returns all the known resources.
   */
  public Map<URI, URL> knownURLs() {
    return Collections.unmodifiableMap(knownURLs);
  }

  /**
   * Returns the set of schemas that have already been validated or are in the
   * process of being validated.
   *
   * @return the set of validated schemas.
   */
  public Set<URI> validatedSchemas() {
    return Collections.unmodifiableSet(validatedSchemas);
  }

  /**
   * Returns the current base URI. This is the base URI of the closest ancestor.
   */
  public URI baseURI() {
    return state.baseURI;
  }

  public URI recursiveBaseURI() {
    return state.prevRecursiveBaseURI;
  }

  /**
   * Sets the base URI by resolving the given URI with the current base URI.
   * This does not change the recursive base URI.
   *
   * @param uri the new relative base URI
   */
  public void setBaseURI(URI uri) {
    state.baseURI = state.baseURI.resolve(uri);
    // Note: Don't set the state's absKeywordLocation here
  }

  /**
   * Sets the recursive base URI to the current base URI. This bumps the current
   * recursive base to be the previous recursive base, unless it's {@code null},
   * in which case they will be set to the same thing.
   */
  public void setRecursiveBaseURI() {
    if (state.recursiveBaseURI == null) {
      state.prevRecursiveBaseURI = state.baseURI;
    } else {
      state.prevRecursiveBaseURI = state.recursiveBaseURI;
    }
    state.recursiveBaseURI = state.baseURI;
  }

  /**
   * Returns the current specification in use.
   */
  public Specification specification() {
    return state.spec;
  }

  /**
   * Sets the current specification. This controls how the schema is processed.
   *
   * @param spec the new specification
   */
  public void setSpecification(Specification spec) {
    Objects.requireNonNull(spec);

    state.spec = spec;
  }

  /**
   * Sets a vocabulary as required or optional. Set {@code true} for required
   * and {@code false} for optional.
   * <p>
   * This returns whether the ID is unique. If not unique then the new value is
   * not set.
   *
   * @param id the vocabulary ID
   * @param required whether the vocabulary is required or optional
   */
  public boolean setVocabulary(URI id, boolean required) {
    return vocabularies.putIfAbsent(id, required) == null;
  }

  /**
   * Returns all the known vocabularies and whether they're required.
   */
  public Map<URI, Boolean> vocabularies() {
    return Collections.unmodifiableMap(vocabularies);
  }

  /**
   * Returns whether the parent object of the current keyword is the
   * root schema.
   */
  public boolean isRootSchema() {
    return state.isRoot;
  }

  /**
   * Returns the location of the parent of the current keyword. This is the
   * location of the containing object.
   * <p>
   * Note that this returns the dynamic path and not a resolved URI. This is
   * meant for annotations. Callers need to resolve against the base URI if
   * they need an absolute form.
   */
  public String schemaParentLocation() {
    return state.keywordParentLocation;
  }

  /**
   * Returns the location of the current keyword. This is the location of the
   * current object.
   * <p>
   * Note that this returns the dynamic path and not a resolved URI. This is
   * meant for annotations. Callers need to resolve against the base URI if
   * they need an absolute form.
   */
  public String schemaLocation() {
    return state.keywordLocation;
  }

  /**
   * Sets whether to collect annotations in subschemas. This is an optimization
   * that can be called when a keyword knows no further annotations should
   * be collected.
   *
   * @param flag the new state
   */
  public void setCollectSubAnnotations(boolean flag) {
    state.isCollectSubAnnotations = flag;
  }

  /**
   * Finds the element associated with the given ID. If there is no such element
   * having the ID then this returns {@code null}. If the returned element was
   * from a new resource and is a schema then the current state will be set as
   * the root.
   * <p>
   * The search order is as follows:
   * <ol>
   * <li>Known JSON contents</li>
   * <li>Known resources</li>
   * <li>Predefined known resources such as known schemas</li>
   * </ol>
   * <p>
   * If the ID has a fragment then it will be removed prior to searching the
   * other known resources.
   *
   * @param id the id
   * @return the element having the given ID, or {@code null} if there's no
   *         such element.
   */
  public JsonElement findAndSetRoot(URI id) {
    JsonElement e = knownIDs.get(new Id(id));
    if (e != null) {
      return e;
    }

    // Strip off the fragment, but after we know we don't know about it
    id = URIs.stripFragment(id);

    // Walk backwards until we find a matching resource or we hit the beginning
    StringBuilder sb = new StringBuilder();
    URI uri = id;
    String path = uri.getRawPath();
    while (true) {
      // Try the resource
      URL url = knownURLs.get(uri);
      if (url != null) {
        try (InputStream in = new URL(url.toString() + sb.toString()).openStream()) {
          state.schemaObject = null;
          return JSON.parse(in);
        } catch (IOException | JsonParseException ex) {
          // Ignore and try next
        }
      }

      if (path == null || path.isEmpty()) {
        break;
      }

      // Reduce the path
      path = uri.getRawPath();
      int lastSlashIndex = path.lastIndexOf('/');
      if (lastSlashIndex >= 0) {
        if (sb.length() > 0) {
          sb.insert(0, '/');
        }
        sb.insert(0, path.substring(lastSlashIndex + 1));
        try {
          uri = new URI(uri.getScheme(), uri.getRawAuthority(), path.substring(0, lastSlashIndex),
                        uri.getRawQuery(), null);
        } catch (URISyntaxException ex) {
          // Something's wrong, so ignore and continue the search
          break;
        }
      }
    }

    e = Validator.loadResource(id);
    if (e != null && Validator.isSchema(e)) {
      state.schemaObject = null;
    }
    return e;
  }

  /**
   * Finds the complete {@link Id} given a known ID URI. This is used to help
   * transform an anchor to a canonical URI. To do this transformation, combine
   * the base URI with the path as a fragment.
   *
   * @param id the ID URI
   * @return the complete {@link Id} associated with the URI.
   * @see Id
   */
  public Id findID(URI id) {
    return idsByURI.get(id);
  }

  /**
   * Adds an annotation to the current instance location. This throws a
   * {@link MalformedSchemaException} if the value is not unique. This helps
   * detect infinite loops.
   * <p>
   * This does not add the annotations if the context is not configured to
   * do so.
   *
   * @param name the annotation name
   * @param value the annotation value
   * @throws MalformedSchemaException if the addition is not unique.
   */
  public void addAnnotation(String name, Object value) throws MalformedSchemaException {
    if (!isCollectAnnotations || !state.isCollectSubAnnotations) {
      return;
    }

    Annotation a = new Annotation(name);
    a.instanceLocation = state.instanceLocation;
    a.keywordLocation = state.keywordLocation;
    a.absKeywordLocation = state.absKeywordLocation;
    a.value = value;

    Annotation oldA = annotations
        .computeIfAbsent(state.instanceLocation, k -> new HashMap<>())
        .computeIfAbsent(name, k -> new HashMap<>())
        .putIfAbsent(state.keywordLocation, a);
    if (oldA != null) {
      throw new MalformedSchemaException("annotation not unique: possible infinite loop",
                                         state.absKeywordLocation);
    }
  }

  /**
   * Adds an annotation whether annotation collection is disabled or not, and
   * local only to the current schema instance and not any of its descendants.
   * <p>
   * Some keywords depend on the presence of "local" annotations that apply to
   * the current schema.
   *
   * @param name the annotation name
   * @param value the annotation value
   * @throws MalformedSchemaException if the addition is not unique.
   */
  public void addLocalAnnotation(String name, Object value) throws MalformedSchemaException {
    if (state.localAnnotations.putIfAbsent(name, value) != null) {
      throw new MalformedSchemaException("local annotation not unique: possible infinite loop",
                                         state.absKeywordLocation);
    }
  }

  /**
   * Adds an error annotation to the current instance location. This throws a
   * {@link MalformedSchemaException} if the value is not unique. This helps
   * detect infinite loops. This should not be called more than once
   * per keyword.
   * <p>
   * The message can be {@code null} to indicate no message.
   * <p>
   * The annotation will be named "error" for failed validations and
   * "annotation" for successful validations.
   *
   * @param result the validation result
   * @param message the error message, may be {@code null}
   * @throws MalformedSchemaException if the addition is not unique.
   */
  public void addError(boolean result, String message) throws MalformedSchemaException {
    if (!isCollectErrors) {
      return;
    }

    Annotation a = new Annotation(result ? "annotation" : "error");
    a.instanceLocation = state.instanceLocation;
    a.keywordLocation = state.keywordLocation;
    a.absKeywordLocation = state.absKeywordLocation;
    a.value = new ValidationResult(result, message);

    Annotation oldA = errors
        .computeIfAbsent(state.instanceLocation, k -> new HashMap<>())
        .putIfAbsent(state.keywordLocation, a);
    if (oldA != null) {
      throw new MalformedSchemaException("error not unique: possible infinite loop",
                                         state.absKeywordLocation);
    }
  }

  /**
   * Returns whether there's an existing annotation having the given name at the
   * current instance location.
   *
   * @param name the annotation name
   * @return whether there's an existing annotation.
   */
  public boolean hasAnnotation(String name) {
    if (!isCollectAnnotations || !state.isCollectSubAnnotations) {
      return false;
    }

    return annotations
        .getOrDefault(state.instanceLocation, Collections.emptyMap())
        .getOrDefault(name, Collections.emptyMap())
        .containsKey(state.keywordLocation);
  }

  /**
   * Returns whether there's an existing error at the current instance location.
   */
  public boolean hasError() {
    if (!isCollectErrors) {
      return false;
    }

    return errors
        .getOrDefault(state.instanceLocation, Collections.emptyMap())
        .containsKey(state.keywordLocation);
  }

  /**
   * Gets the all the annotations attached to the current instance location for
   * the given name.
   *
   * @param name the annotation name
   * @return a map keyed by schema location.
   */
  public Map<String, Annotation> annotations(String name) {
    if (!isCollectAnnotations) {
      return Collections.emptyMap();
    }

    return Collections.unmodifiableMap(
        annotations
            .getOrDefault(state.instanceLocation, Collections.emptyMap())
            .getOrDefault(name, Collections.emptyMap()));
  }

  /**
   * Gets the local annotation having the given name. This returns {@code null}
   * if the annotation does not exist.
   *
   * @param name the annotation name
   * @return the local annotation, or {@code null} if there's no annotation by
   *         that name.
   */
  public Object localAnnotation(String name) {
    return state.localAnnotations.get(name);
  }

  /**
   * Merges the path with the base URI. If the given path is empty, this returns
   * the base URI. It is assumed that the base contains an absolute part and a
   * a JSON Pointer part in the fragment.
   *
   * @param base the base URI
   * @param path path to append
   * @return the merged URI.
   */
  private static URI resolveAbsolute(URI base, String path) {
    if (path.isEmpty()) {
      return base;
    }
    if (path.startsWith("/")) {
      return base.resolve("#" + path);
    }
    String fragment = base.getRawFragment();
    if (fragment == null) {
      fragment = "";
    }
    return base.resolve("#" + fragment + "/" + Strings.pctEncodeFragment(path));
  }

  /**
   * Merges the path with the base pointer. If the given path is empty, this
   * returns the base pointer. It is assumed that the base path is a
   * JSON Pointer.
   * <p>
   * This also ensures that the path is a valid JSON Pointer by escaping any
   * characters as needed.
   *
   * @param base the base pointer
   * @param path path to append
   * @return the merged pointer, escaping the path as needed.
   */
  private static String resolvePointer(String base, String path) {
    if (path.isEmpty()) {
      return base;
    }
    path = path.replace("~", "~0");
    path = path.replace("/", "~1");
    return base + "/" + path;
  }

  /**
   * Throws a {@link MalformedSchemaException} with the given message for the
   * current state. The error will be tagged with the current absolute
   * keyword location.
   *
   * @param err the error message
   * @param path the location of the element, "" to use the current
   * @throws MalformedSchemaException always.
   */
  public void schemaError(String err, String path) throws MalformedSchemaException {
    throw new MalformedSchemaException(err, resolveAbsolute(state.absKeywordLocation, path));
  }

  /**
   * A convenience method that calls {@link #schemaError(String, String)} with a
   * path of {@code ""}, indicating the current path.
   *
   * @param err the error message
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void schemaError(String err) throws MalformedSchemaException {
    schemaError(err, "");
  }

  /**
   * Checks whether the given JSON element is a valid JSON Schema. If it is not
   * then a schema error will be flagged using the context. A valid schema can
   * either be an object or a Boolean.
   * <p>
   * Note that this is just a rudimentary check for the base type. It is assumed
   * that the schema will have been deeply checked against the meta-schema.
   * <p>
   * A path can be specified that indicates where the relative location of the
   * checked element. For example, {@code ""} means "current element" and
   * {@code "../then"} means "element named 'then' under the parent of the
   * current element."
   *
   * @param e the JSON element to test
   * @param path the location of the element, "" to use the current
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void checkValidSchema(JsonElement e, String path) throws MalformedSchemaException {
    if (!Validator.isSchema(e)) {
      throw new MalformedSchemaException("not a valid JSON Schema",
                                         resolveAbsolute(state.absKeywordLocation, path));
    }
  }

  /**
   * A convenience method that calls {@link #checkValidSchema(JsonElement, String)}
   * with a path of {@code ""}, indicating the current path.
   *
   * @param e the JSON element to test
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void checkValidSchema(JsonElement e) throws MalformedSchemaException {
    checkValidSchema(e, "");
  }

  /**
   * Follows a JSON Pointer into a JSON element and returns the requested
   * sub-element. It is expected that {@code ptr} is a valid JSON Pointer.
   * <p>
   * This sets the base URI along the way as it passes each $id, starting from
   * the given base URI. This ignores any child $id at the root level because
   * that's how we got here in the first place; we don't need to process
   * it again.
   *
   * @param baseURI the starting point for any new base URI
   * @param e the element to traverse
   * @param ptr the JSON Pointer
   * @return the specified sub-element, or {@code null} if not found.
   * @throws MalformedSchemaException if an invalid $id was encountered
   */
  public JsonElement followPointer(URI baseURI, JsonElement e, String ptr)
      throws MalformedSchemaException {
    int i = -1;
    StringBuilder path = new StringBuilder();
    URI newBase = baseURI;

    // Split using a negative limit so that trailing empty strings are allowed
    for (String part : ptr.split("/", -1)) {
      i++;

      // Only ignore the first empty string, the one before the initial "/"
      // All others could be zero-length member names
      if (i == 0) {
        if (part.isEmpty()) {
          continue;
        }
      }
      path.append('/').append(part);

      if (e == null) {
        return null;
      }
      try {
        int index = Integer.parseInt(part);
        if (!e.isJsonArray()) {
          return null;
        }
        if (index >= e.getAsJsonArray().size()) {
          return null;
        }
        e = e.getAsJsonArray().get(index);
        continue;
      } catch (NumberFormatException ex) {
        // Nothing, skip to name processing
      }

      if (!e.isJsonObject()) {
        return null;
      }

      JsonElement idElem = e.getAsJsonObject().get(CoreId.NAME);
      if (i > 1 && idElem != null && JSON.isString(idElem)) {
        URI id = getID(idElem, path + "/" + CoreId.NAME);
        if (id != null) {
          newBase = newBase.resolve(id);
        }
      }

      // Transform the part
      part = part.replace("~0", "~");
      part = part.replace("~1", "/");
      e = e.getAsJsonObject().get(part);
    }
    if (e != null) {
      state.baseURI = newBase;
      // Note: Don't set the state's absKeywordLocation here
    }
    return e;
  }

  /**
   * Gets and processes the given ID element. This returns a URI suitable for
   * resolving against the current base URI. This will return {@code null} if
   * the ID does not represent a new base, for example if it's an anchor.
   *
   * @param idElem the ID element
   * @param path the relative path of the element, may be empty
   * @return the processed ID, or {@code null} if it's not a new base.
   * @throws MalformedSchemaException if the ID is malformed.
   */
  public URI getID(JsonElement idElem, String path) throws MalformedSchemaException {
    return Validator.getID(idElem, specification(),
                           resolveAbsolute(state.absKeywordLocation, path));
  }

  /**
   * Applies a schema to the given instance. The schema and instance path
   * parameters are the relative element name, either a name or a number. An
   * empty string means the current location.
   * <p>
   * This first checks that the schema is valid. A valid schema is either an
   * object or a Boolean.
   * <p>
   * The {@code absSchemaLoc} parameter is used as the new absolute keyword
   * location, unless there's a declared $id, in which case that value is used.
   * If there's no $id and the parameter is {@code null} then the location will
   * be assigned the schema path resolved against the current location,
   * as usual.
   *
   * @param schema the schema, an object or a Boolean
   * @param schemaPath the schema path
   * @param absSchemaLoc the new absolute location, or {@code null}
   * @param instance the instance element
   * @param instancePath the instance path
   * @throws MalformedSchemaException if the schema is not valid. This could be
   *         because it doesn't validate against any declared meta-schema or
   *         because internal validation is failing.
   */
  public boolean apply(JsonElement schema, String schemaPath, URI absSchemaLoc,
                       JsonElement instance, String instancePath)
      throws MalformedSchemaException
  {
    if (JSON.isBoolean(schema)) {
      return schema.getAsBoolean();
    }

    URI absKeywordLocation = null;

    // See if the absolute keyword location needs to change
    if (schema.isJsonObject()) {
      JsonElement idElem = schema.getAsJsonObject().get(CoreId.NAME);
      if (idElem != null) {
        URI id = getID(idElem, schemaPath + "/" + CoreId.NAME);
        if (id != null) {
          absKeywordLocation = baseURI.resolve(id);
        }
      }
    }

    if (absKeywordLocation == null) {
      if (absSchemaLoc == null) {
        absKeywordLocation = resolveAbsolute(state.absKeywordLocation, schemaPath);
      } else {
        absKeywordLocation = absSchemaLoc;
      }
    }
    if (!schema.isJsonObject()) {
      throw new MalformedSchemaException("not a valid JSON Schema", absKeywordLocation);
    }

    // Set this here because callers may need this
    state.absKeywordLocation = absKeywordLocation;

    JsonObject schemaObject = schema.getAsJsonObject();
    if (schemaObject.size() == 0) {  // Empty schemas always validate
      return true;
    }

    String keywordLocation = resolvePointer(state.keywordLocation, schemaPath);
    String instanceLocation = resolvePointer(state.instanceLocation, instancePath);

    State parentState = state;
    state = state.copy();
    state.isRoot = (state.schemaObject == null);
    state.schemaObject = schemaObject;
    state.keywordParentLocation = keywordLocation;
    state.instanceLocation = instanceLocation;

    // Sort the names in the schema by their required evaluation order
    // Also, more fun with streams
    var ordered = schemaObject.entrySet().stream()
        .filter(e -> keywords.containsKey(e.getKey()))
        .sorted(Comparator.comparing(e -> keywordClasses.get(e.getKey())))
        .collect(Collectors.toList());

    boolean result = true;

    for (var m : ordered) {
      Keyword k = keywords.get(m.getKey());

      // $ref causes all other properties to be ignored
      if (specification().ordinal() < Specification.DRAFT_2019_09.ordinal()) {
        if (schemaObject.has(CoreRef.NAME) && !k.name().equals(CoreRef.NAME)) {
          continue;
        }
      }

      state.keywordLocation = resolvePointer(keywordLocation, m.getKey());
      state.absKeywordLocation = resolveAbsolute(absKeywordLocation, m.getKey());
      if (!k.apply(m.getValue(), instance, state.schemaObject, this)) {
        // Remove all subschema annotations that aren't errors
        // Note that this is still necessary even with the setCollectAnnotations
        // optimization because it either may not be used or not used
        // early enough
        if (isCollectAnnotations) {
          annotations.getOrDefault(instanceLocation, Collections.emptyMap())
              .values()
              .forEach(
                  v -> v.entrySet().removeIf(e -> e.getKey().startsWith(state.keywordLocation)));
        }
        if (!hasError()) {
          addError(false, k.name() + " didn't validate");
        }

        // Don't escape early because we need to process all the keywords
        result = false;
      } else if (!hasError()) {
        addError(true, k.name());
      }
    }

    state = parentState;
    return result;
  }
}
