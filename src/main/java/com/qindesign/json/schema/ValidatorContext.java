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
import com.qindesign.json.schema.util.Ecma262Pattern;
import com.qindesign.net.URI;
import com.qindesign.net.URISyntaxException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;
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

    // Parent state, may be null
    JSONPath keywordParentLocation;
    URI absKeywordParentLocation;

    Locator loc;

    /** Flag that indicates whether to collect annotations, an optimization. */
    boolean isCollectAnnotations;

    /**
     * Flag that indicates whether to collect annotations of any subschemas.
     * Child states will set their {@link #isCollectAnnotations} according to
     * this value. This is an optimization.
     */
    boolean isCollectSubAnnotations;

    /**
     * Annotations local only to this schema instance and none of
     * its descendants.
     */
    final Map<String, Object> localAnnotations = new HashMap<>();

    /**
     * Creates a new, empty, state object.
     */
    State() {
    }

    /**
     * Copy constructor. This does not copy anything that needs to
     * remain "local".
     */
    State(State state) {
      this.schemaObject = state.schemaObject;
      this.isRoot = state.isRoot;
      this.baseURI = state.baseURI;
      this.spec = state.spec;
      this.prevRecursiveBaseURI = state.prevRecursiveBaseURI;
      this.recursiveBaseURI = state.recursiveBaseURI;
      this.keywordParentLocation = state.keywordParentLocation;
      this.absKeywordParentLocation = state.absKeywordParentLocation;
      this.loc = state.loc;
      this.isCollectAnnotations = state.isCollectAnnotations;
      this.isCollectSubAnnotations = state.isCollectSubAnnotations;
    }
  }

  static {
    // First, load all the known keywords
    keywords = Collections.unmodifiableMap(findKeywords());
    // The 'keywords' set now contains all the keywords

    // Fun with streams
    // Map each keyword to its "class number", so we can sort easily
    Map<String, Integer> classes = IntStream.range(0, KEYWORD_SETS.size())
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
  private static Map<String, Keyword> findKeywords() {
    Set<Class<?>> keywordClasses;
    try (ScanResult scanResult = new ClassGraph()
        .acceptPackagesNonRecursive(CLASS.getPackageName() + ".keywords")
        .removeTemporaryFilesAfterScan()
        .scan()) {
      keywordClasses = scanResult.getAllClasses().stream()
          .map(classInfo -> {
            try {
              return Class.forName(classInfo.getName());
            } catch (ClassNotFoundException ex) {
              // Shouldn't happen, in theory
              throw new IllegalStateException(ex);
            }
          })
          .filter(Keyword.class::isAssignableFrom)
          .collect(Collectors.toSet());
    }

    Map<String, Keyword> keywords = new HashMap<>();

    for (Class<?> c : keywordClasses) {
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
   * <p>
   * {@code null} to not collect annotations.
   */
  private Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations;

  /**
   * Error "annotation" collection, maps element location to its errors:<br>
   * instance location -> schema location -> value
   * <p>
   * {@code null} to not collect errors.
   */
  private Map<JSONPath, Map<JSONPath, Error<?>>> errors;

  /** The main schema used for validation. */
  private final JsonElement schema;

  /** Whether the current schema is a meta-schema. */
  private final boolean isMetaSchema;

  /** The current processing state. */
  private State state;

  private final Map<URI, Id> knownIDs;
  private final Map<URI, URL> knownURLs;

  /** Ids are unique and it's guaranteed that there's only one anchorless ID. */
  private final IdentityHashMap<JsonElement, Set<Id>> idsByElem;

//  // https://www.baeldung.com/java-sneaky-throws
//  @SuppressWarnings("unchecked")
//  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
//    throw (E) e;
//  }

  /**
   * The URL cache.
   */
  private final Map<URL, JsonElement> urlCache = new HashMap<>();

  /**
   * Tracks schemas that have either been validated or in the process of being
   * validated, to avoid $schema recursion.
   * <p>
   * This being non-empty is a good indicator that this is a meta-schema.
   */
  private final Set<URI> validatedSchemas;

  // Options
  private final Options options;
  private final boolean isCollectFailedAnnotations;

  /**
   * The pattern cache.
   */
  private final Map<String, java.util.regex.Pattern> patternCache = new HashMap<>();

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
   * @param schema the schema having the base URI
   * @param isMetaSchema if the schema being validated should be treated as
   *                     a meta-scehma
   * @param knownIDs known JSON contents, must be modifiable
   * @param knownURLs known resources
   * @param validatedSchemas the set of validated schemas
   * @param options any options
   * @throws IllegalArgumentException if the base URI is not absolute or if it
   *         has a non-empty fragment, or if there are any duplicate IDs.
   * @throws NullPointerException if any of the arguments is {@code null}.
   */
  public ValidatorContext(URI baseURI, JsonElement schema, boolean isMetaSchema,
                          Map<URI, Id> knownIDs, Map<URI, URL> knownURLs,
                          Set<URI> validatedSchemas, Options options) {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(knownIDs, "knownIDs");
    Objects.requireNonNull(knownURLs, "knownURLs");
    Objects.requireNonNull(validatedSchemas, "validatedSchemas");
    Objects.requireNonNull(options, "options");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }
    if (URIs.hasNonEmptyFragment(baseURI)) {
      throw new IllegalArgumentException("baseURI has a non-empty fragment");
    }

    baseURI = baseURI.normalize();
    this.schema = schema;
    this.knownIDs = knownIDs;

    // Gather the known IDs by element
    this.idsByElem = new IdentityHashMap<>();
    Id rootID = null;
    if (!knownIDs.isEmpty()) {
      for (Id id : knownIDs.values()) {
        // Ensure:
        // 1. IDs are unique
        // 2. There exists only one anchorless ID
        Set<Id> ids = idsByElem.computeIfAbsent(id.element, elem -> new HashSet<>());

        // Ensure only one anchorless ID
        if (id.id.rawFragment() == null) {
          if (ids.stream().anyMatch(x -> x.id.rawFragment() == null)) {
            throw new IllegalArgumentException("Duplicate known ID: " + baseURI + ": " + id.id);
          }
        }

        // Ensure no duplicates
        if (!ids.add(id)) {
          throw new IllegalArgumentException("Duplicate known ID: " + baseURI + ": " + id.id);
        }

        // Note the root ID
        if (id.path.isEmpty()) {
          if (rootID != null) {
            throw new IllegalArgumentException("Duplicate root ID: " + baseURI + ": " + id.rootURI);
          }
          // TODO: Verify that the unresolved ID is the same as the unresolved rootID
          rootID = new Id(id.rootURI, null, id.unresolvedID,
                          null, JSONPath.absolute(), id.element,
                          id.rootID, id.rootURI);
        }
      }
    } else {
      rootID = new Id(baseURI, null, baseURI, null, JSONPath.absolute(), schema, null, baseURI);
    }

    // Add the base URI
    if (rootID != null) {
      this.knownIDs.putIfAbsent(rootID.id, rootID);
    }

    this.knownURLs = knownURLs;
    this.validatedSchemas = validatedSchemas;

    this.isMetaSchema = isMetaSchema;
    state = new State();
    state.baseURI = baseURI;
    state.spec = (Specification) options.get(Option.DEFAULT_SPECIFICATION);
    state.prevRecursiveBaseURI = null;
    state.recursiveBaseURI = null;
    state.schemaObject = null;
    state.isRoot = true;
    state.keywordParentLocation = null;
    state.absKeywordParentLocation = null;

    URI absKeywordLocation;
    if (rootID == null) {
      absKeywordLocation = baseURI;
    } else {
      if (rootID.rootID == null) {
        absKeywordLocation = rootID.id;
      } else {
        absKeywordLocation = rootID.rootID;
      }
    }
    state.loc = new Locator(JSONPath.absolute(), JSONPath.absolute(), absKeywordLocation);

    state.isCollectAnnotations = true;
    state.isCollectSubAnnotations = true;

    // Options
    this.options = new Options(options);
    isCollectFailedAnnotations = isOption(Option.COLLECT_ANNOTATIONS_FOR_FAILED);
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
   * Returns whether annotations are being collected.
   *
   * @return whether annotations are being collected.
   */
  public boolean isCollectAnnotations() {
    return annotations != null;
  }

  /**
   * Returns whether we can fail fast when processing a keyword.
   *
   * @return whether we can fail fast during keyword processing.
   */
  public boolean isFailFast() {
    return (annotations == null) && (errors == null);
  }

  /**
   * Returns the cached compiled pattern for the given regex. This will create
   * one via {@link java.util.regex.Pattern#compile(String)} if it does not
   * exist in the cache.
   * <p>
   * This first translates the regex from ECMA-262 into Java (a best-effort
   * attempt) before compiling the pattern. This means that the returned
   * {@link java.util.regex.Pattern pattern}'s internal
   * {@link java.util.regex.Pattern#pattern() pattern string} may be different
   * than the argument.
   *
   * @param regex the regex for which to retrieve the compiled pattern
   * @return the associated cached compiled pattern.
   * @throws PatternSyntaxException for a bad pattern.
   * @see Ecma262Pattern#translate(String)
   */
  public java.util.regex.Pattern pattern(String regex) {
    return patternCache.computeIfAbsent(Ecma262Pattern.translate(regex),
                                        java.util.regex.Pattern::compile);
  }

  /**
   * Returns all the known resources.
   *
   * @return all the known resources.
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
   * Returns whether the current schema being processed is being treated as
   * a meta-schema.
   *
   * @return whether the current schema is a meta-schema.
   */
  public boolean isMetaSchema() {
    return isMetaSchema;
  }

  /**
   * Returns the current base URI. This is the base URI of the closest ancestor.
   *
   * @return the current base URI.
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
   *
   * @return the current specification.
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
   * This returns whether the ID is unique. If not unique, then the new value is
   * not set.
   *
   * @param id the vocabulary ID
   * @param required whether the vocabulary is required or optional
   * @return whether the ID is unique.
   */
  public boolean setVocabulary(URI id, boolean required) {
    return vocabularies.putIfAbsent(id, required) == null;
  }

  /**
   * Returns all the known vocabularies and whether they're required.
   *
   * @return all the known vocabularies.
   */
  public Map<URI, Boolean> vocabularies() {
    return Collections.unmodifiableMap(vocabularies);
  }

  /**
   * Returns whether the parent object of the current keyword is the
   * root schema.
   *
   * @return whether the parent of the current keyword is the root schema.
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
   *
   * @return the location of the parent of the current keyword.
   */
  public JSONPath schemaParentLocation() {
    return state.keywordParentLocation;
  }

  /**
   * Returns the location of the current keyword. This is the location of the
   * current object.
   * <p>
   * Note that this returns the dynamic path and not a resolved URI. This is
   * meant for annotations. Callers need to resolve against the base URI if
   * they need an absolute form.
   *
   * @return the location of the current keyword.
   */
  public JSONPath schemaLocation() {
    return state.loc.keyword;
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
   * Finds the element associated with the given ID and sets a new base URI. If
   * there is no such element having the ID, then this returns {@code null} and
   * the base URI is not set. If the returned element was from a new resource
   * and is a schema, then the current state will be set as the root.
   * <p>
   * The search order is as follows:
   * <ol>
   * <li>Known JSON contents</li>
   * <li>Known resources</li>
   * <li>Predefined known resources such as known schemas</li>
   * </ol>
   * <p>
   * If the ID has a fragment, then it will be removed prior to searching the
   * other known resources.
   *
   * @param id the id
   * @return the element having the given ID, or {@code null} if there's no
   *         such element.
   */
  public JsonElement findAndSetRoot(URI id) {
    Id theID = knownIDs.get(id);
    if (theID != null) {
      state.baseURI = theID.id;
      return theID.element;
    }

    // Strip off the fragment, but after we know we don't know about it
    id = URIs.stripFragment(id);

    // Walk backwards until we find a matching resource or we hit the beginning
    StringBuilder sb = new StringBuilder();
    URI uri = id;
    String path;
    do {
      // Try the resource
      URL url = knownURLs.get(uri);
      // Note that we don't try the URI itself as a URL here (if AUTO_RESOLVE is
      // enabled) because that was already examined in the Validator constructor
      // We also shouldn't do this here because all accessible URLs (at least in
      // this incarnation of the software) need to be pre-scanned for IDs
      // See Validator.prepareSchema

      if (url != null) {
        try {
          JsonElement data = urlCache.computeIfAbsent(
              new URL(url, sb.toString()),
              url2 -> {
                try (InputStream in = url2.openStream()) {
                  return JSON.parse(in);
                } catch (IOException ex) {
                  throw new UncheckedIOException(ex);
                }
              });
          if (data != null) {
            state.schemaObject = null;
            state.baseURI = uri;
            return data;
          }
        } catch (MalformedURLException | UncheckedIOException | JsonParseException ex) {
          // Ignore and try next
        }
      }

      // Reduce the path
      path = uri.rawPath();
      if (path == null) {
        path = "";
      }
      int lastSlashIndex = path.lastIndexOf('/');
      try {
        if (lastSlashIndex >= 0) {
          if (sb.length() > 0) {
            sb.insert(0, '/');
          }
          sb.insert(0, path.substring(lastSlashIndex + 1));
          uri = new URI(uri.scheme(), uri.authority(),
                        path.substring(0, lastSlashIndex),
                        uri.query(), null);
        } else {
          // This case will happen when a Java URI object has a non-absolute
          // path. This may result when resolving by the algorithm in
          // [RFC 2396: 5.2. Resolving Relative References to Absolute Form](https://www.rfc-editor.org/rfc/rfc2396.html#section-5.2).
          // The algorithm in
          // [RFC 3986: 5.2 Relative Resolution](https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2)
          // correctly ensures there's a leading slash. The solution is to
          // define our own resolution function that accounts for this.
          // This has been done with our own URI implementation.
          sb.insert(0, path);
          uri = new URI(uri.scheme(), uri.authority(), "", uri.query(), null);
        }
      } catch (URISyntaxException ex) {
        // Something's wrong, so ignore and continue the search with the
        // predefined resources below
        break;
      }
    } while (!path.isEmpty());

    JsonElement e = Validator.loadResource(id);
    if (e != null && Validator.isSchema(e)) {
      state.schemaObject = null;
      state.baseURI = id;
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
    return knownIDs.get(id);
  }

  /**
   * Adds an annotation to the current instance location. This throws a
   * {@link MalformedSchemaException} if the value is not unique. This helps
   * detect infinite loops.
   * <p>
   * This does not add the annotations if the context is not configured to
   * do so.
   *
   * @param <T> the type of the value
   * @param name the annotation name
   * @param value the annotation value
   * @throws MalformedSchemaException if the addition is not unique.
   */
  public <T> void addAnnotation(String name, T value) throws MalformedSchemaException {
    if (annotations == null) {
      return;
    }
    if (!state.isCollectAnnotations && !isCollectFailedAnnotations) {
      return;
    }

    var a = new Annotation<>(name, state.loc, value);
    a.setValid(state.isCollectAnnotations);

    var oldA = annotations
        .computeIfAbsent(state.loc.instance, k -> new HashMap<>())
        .computeIfAbsent(name, k -> new HashMap<>())
        .putIfAbsent(state.loc.keyword, a);
    if (oldA != null) {
      throw new MalformedSchemaException("annotation not unique: possible infinite loop",
                                         state.loc.absKeyword);
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
                                         state.loc.absKeyword);
    }
  }

  /**
   * Adds an error, a validation result, to the current instance location. This
   * throws a {@link MalformedSchemaException} if the value is not unique. This
   * helps detect infinite loops. This should not be called more than once
   * per keyword.
   * <p>
   * The message can be {@code null} to indicate no message.
   *
   * @param <T> the type of the associated value
   * @param result the validation result
   * @param value the associated value, may be {@code null}
   * @throws MalformedSchemaException if the addition is not unique.
   */
  public <T> void addError(boolean result, T value) throws MalformedSchemaException {
    if (errors == null) {
      return;
    }

    var err = new Error<>(result, state.loc, value);

    var oldErr = errors
        .computeIfAbsent(state.loc.instance, k -> new HashMap<>())
        .putIfAbsent(state.loc.keyword, err);
    if (oldErr != null) {
      throw new MalformedSchemaException("error not unique: possible infinite loop",
                                         state.loc.absKeyword);
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
    if (annotations == null || !state.isCollectAnnotations) {
      return false;
    }

    return annotations
        .getOrDefault(state.loc.instance, Collections.emptyMap())
        .getOrDefault(name, Collections.emptyMap())
        .containsKey(state.loc.keyword);
  }

  /**
   * Returns whether there's an existing error at the current instance location.
   *
   * @return whether there's an error at the current instance location.
   */
  public boolean hasError() {
    if (errors == null) {
      return false;
    }

    return errors
        .getOrDefault(state.loc.instance, Collections.emptyMap())
        .containsKey(state.loc.keyword);
  }

  /**
   * Gets the all the annotations attached to the current instance location for
   * the given name. If annotations are not being collected, then this returns
   * an empty map.
   * <p>
   * Annotations can optionally be checked for
   * {@link Annotation#isValid() validity}.
   *
   * @param name the annotation name
   * @return a map keyed by schema location.
   */
  public Map<JSONPath, Annotation<?>> annotations(String name) {
    if (annotations == null) {
      return Collections.emptyMap();
    }

    return Collections.unmodifiableMap(
        annotations
            .getOrDefault(state.loc.instance, Collections.emptyMap())
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
   * Merges the path with the base URI. If the given path is {@code null}, then
   * this returns the base URI. It is assumed that the base contains an absolute
   * part and a JSON Pointer part in its fragment.
   *
   * @param base the base URI
   * @param path path to append
   * @return the merged URI.
   */
  private static URI resolveAbsolute(URI base, JSONPath path) {
    if (path == null) {
      return base;
    }

    String fragment;
    if (path.isAbsolute()) {
      fragment = path.toString();
    } else {
      fragment = Optional.ofNullable(base.fragment()).orElse("") +
                 "/" + path;
    }
    if (fragment.indexOf('.') >= 0) {  // Very rudimentary check
      fragment = JSONPath.fromJSONPointer(fragment).normalize().toString();
    }
    try {
      return base.resolve(new URI(null, null, null, null, fragment));
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Unexpected bad URI", ex);
    }
  }

  /**
   * Merges the given name with the base URI. If the name is {@code null}, then
   * this returns the base URI. It is assumed that the base contains an absolute
   * part and a JSON Pointer part in its fragment.
   *
   * @param base the base URI
   * @param name the name to append
   * @return the merged URI.
   */
  private static URI resolveAbsolute(URI base, String name) {
    if (name == null) {
      return resolveAbsolute(base, (JSONPath) null);
    }
    return resolveAbsolute(base, JSONPath.fromElement(name));
  }

  /**
   * Merges the given name with the base pointer. If the name is {@code null},
   * then this returns the base pointer.
   *
   * @param base the base pointer
   * @param name the name to append, {@code null} to not append anything
   * @return the merged pointer, escaping the path as needed.
   */
  private static JSONPath resolvePointer(JSONPath base, String name) {
    if (name == null) {
      return base;
    }
    return base.append(name);
  }

  /**
   * Throws a {@link MalformedSchemaException} with the given message for the
   * current state. The error will be tagged with the current absolute
   * keyword location.
   *
   * @param err the error message
   * @param path the relative child element path, {@code null} for the
   *             current element
   * @throws MalformedSchemaException always.
   */
  public void schemaError(String err, JSONPath path) throws MalformedSchemaException {
    throw new MalformedSchemaException(err, resolveAbsolute(state.loc.absKeyword, path));
  }

  /**
   * Calls {@link #schemaError(String, JSONPath)}.
   *
   * @param err the error message
   * @param name the child element name, {@code null} for the current element
   * @throws MalformedSchemaException always.
   */
  public void schemaError(String err, String name) throws MalformedSchemaException {
    schemaError(err, JSONPath.fromElement(name));
  }

  /**
   * A convenience method that calls {@link #schemaError(String, String)} with a
   * name of {@code null}, indicating the current element.
   *
   * @param err the error message
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void schemaError(String err) throws MalformedSchemaException {
    schemaError(err, (JSONPath) null);
  }

  /**
   * Checks whether the given JSON element is a valid JSON Schema. If it is not,
   * then a schema error will be flagged using the context. A valid schema can
   * either be an object or a Boolean.
   * <p>
   * Note that this is just a rudimentary check for the base type. It is assumed
   * that the schema will have been deeply checked against the meta-schema.
   *
   * @param e the JSON element to test
   * @param name the child element name, {@code null} for the current element
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void checkValidSchema(JsonElement e, String name) throws MalformedSchemaException {
    if (!Validator.isSchema(e)) {
      throw new MalformedSchemaException("not a valid JSON Schema",
                                         resolveAbsolute(state.loc.absKeyword, name));
    }
  }

  /**
   * A convenience method that calls {@link #checkValidSchema(JsonElement, String)}
   * with a name of {@code null}, indicating the current element.
   *
   * @param e the JSON element to test
   * @throws MalformedSchemaException if the given element is not a
   *         valid schema.
   */
  public void checkValidSchema(JsonElement e) throws MalformedSchemaException {
    checkValidSchema(e, null);
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
   */
  public JsonElement followPointer(URI baseURI, JsonElement e, String ptr) {
    if (e == null) {
      return null;
    }

    URI newBase = baseURI;

    for (String part : JSONPath.fromJSONPointer(ptr)) {
      // First try as an array index
      try {
        int index = Integer.parseInt(part);
        if (e.isJsonArray()) {
          if (index >= e.getAsJsonArray().size()) {
            return null;
          }
          e = e.getAsJsonArray().get(index);
          continue;
        }
      } catch (NumberFormatException ex) {
        // Nothing, skip to name processing
      }

      if (!e.isJsonObject()) {
        return null;
      }

      Optional<Id> id = idsByElem.computeIfAbsent(e, elem -> Collections.emptySet()).stream()
          .filter(x -> x.id.rawFragment() == null)
          .findFirst();
      if (id.isPresent()) {
        newBase = id.get().id;
      }

      // Transform the part
      e = e.getAsJsonObject().get(part);
      if (e == null) {
        return null;
      }
    }

    state.baseURI = newBase;
    // Note: Don't set the state's absKeywordLocation here

    return e;
  }

  /**
   * Applies the schema to the given instance. To collect annotations or errors,
   * pass a non-{@code null} value for the respective parameter. If errors are
   * collected, then both valid and invalid results are collected.
   * <p>
   * This calls {@link #apply(JsonElement, String, URI, JsonElement, String)}
   * after setting the annotations and errors collection vessels.
   * </p>
   *
   * @param instance the instance to be validated
   * @param annotations annotations get stored here, may be {@code null} for
   *                    no collection
   * @param errors errors get stored here, may be {@code null} for no collection
   * @return the schema application result.
   * @throws MalformedSchemaException if the schema is not valid. This could be
   *         because it doesn't validate against any declared meta-schema or
   *         because internal validation is failing.
   */
  public boolean apply(JsonElement instance,
                       Map<JSONPath, Map<String, Map<JSONPath, Annotation<?>>>> annotations,
                       Map<JSONPath, Map<JSONPath, Error<?>>> errors)
      throws MalformedSchemaException {
    this.annotations = annotations;
    this.errors = errors;

    boolean retval = apply(schema, null, null, instance, null);
    if (retval) {
      addError(true, null);
    } else {
      addError(false, "schema didn't validate");
    }

    // Clean up any empty annotations
    if (annotations != null) {
      annotations.forEach((key, value) -> {
        value.entrySet().removeIf(e -> e.getValue().isEmpty());
      });
      annotations.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    return retval;
  }

  /**
   * Applies a schema to the given instance. The keyword and instance name
   * parameters are the relative element name, either a name or a number. An
   * empty string means the current location.
   * <p>
   * This first checks that the schema is valid. A valid schema is either an
   * object or a Boolean.
   * <p>
   * The {@code absSchemaLoc} parameter is used as the new absolute keyword
   * location, unless there's a declared $id, in which case that value is used.
   * If there's no $id and the parameter is {@code null}, then the location will
   * be assigned the keyword name resolved against the current location,
   * as usual.
   * <p>
   * This does not set the annotations or errors collection vessels, and is only
   * designed to be used from keywords.
   *
   * @param schema the schema, an object or a Boolean
   * @param name the keyword name, {@code null} for the current element
   * @param absSchemaLoc the new absolute location, or {@code null}
   * @param instance the instance element
   * @param instanceName the instance element name, {@code null} for the
   *                     current element
   * @return the result of schema application.
   * @throws MalformedSchemaException if the schema is not valid. This could be
   *         because it doesn't validate against any declared meta-schema or
   *         because internal validation is failing.
   */
  public boolean apply(JsonElement schema, String name, URI absSchemaLoc,
                       JsonElement instance, String instanceName)
      throws MalformedSchemaException
  {
    if (JSON.isBoolean(schema)) {
      return schema.getAsBoolean();
    }

    URI absKeywordLocation = null;

    // See if the absolute keyword location and base URI needs to change
    // Note: The base URI will change when CoreId is executed
    if (schema.isJsonObject()) {
      Optional<Id> id = idsByElem.computeIfAbsent(schema, elem -> Collections.emptySet()).stream()
          .filter(x -> x.id.rawFragment() == null)
          .findFirst();
      if (id.isPresent()) {
        absKeywordLocation = id.get().id;
        state.baseURI = id.get().id;
      }
    }

    if (absKeywordLocation == null) {
      if (absSchemaLoc == null) {
        absKeywordLocation = resolveAbsolute(state.loc.absKeyword, name);
      } else {
        absKeywordLocation = absSchemaLoc;
      }
    }
    if (!schema.isJsonObject()) {
      throw new MalformedSchemaException("not a valid JSON Schema", absKeywordLocation);
    }

    JsonObject schemaObject = schema.getAsJsonObject();
    if (schemaObject.size() == 0) {  // Empty schemas always validate
      return true;
    }

    JSONPath keywordLocation = resolvePointer(state.loc.keyword, name);
    JSONPath instanceLocation = resolvePointer(state.loc.instance, instanceName);

    State parentState = state;
    state = new State(state);
    state.isRoot = (state.schemaObject == null);
    state.schemaObject = schemaObject;
    state.keywordParentLocation = keywordLocation;
    state.absKeywordParentLocation = absKeywordLocation;
    state.loc = new Locator(instanceLocation, state.loc.keyword, absKeywordLocation);
    state.isCollectAnnotations = parentState.isCollectSubAnnotations;
    state.isCollectSubAnnotations = state.isCollectAnnotations;

    // Sort the names in the schema by their required evaluation order
    // Also, more fun with streams
    List<Map.Entry<String, JsonElement>> ordered = schemaObject.entrySet().stream()
        .filter(e -> keywords.containsKey(e.getKey()))
        .sorted(Comparator.comparing(e -> keywordClasses.get(e.getKey())))
        .collect(Collectors.toList());

    boolean result = true;

    for (var m : ordered) {
      Keyword k = keywords.get(m.getKey());

      // $ref causes all other properties to be ignored
      if (specification().compareTo(Specification.DRAFT_2019_09) < 0) {
        if (schemaObject.has(CoreRef.NAME) && !k.name().equals(CoreRef.NAME)) {
          continue;
        }
      }

      if (!applyKeyword(k, m.getValue(), instance)) {
        // Don't escape early if we need to process all the keywords
        result = false;
        if (isFailFast()) {
          break;
        }
      }
    }

    // Remove all schema annotations if the schema failed
    if (!result && annotations != null) {
      // Note that we're also checking for equality in case the current
      // failing keyword has set some annotations
      Predicate<Map.Entry<JSONPath, Annotation<?>>> pred =
          e -> e.getKey().startsWith(keywordLocation);

      if (!isCollectFailedAnnotations) {
        annotations.getOrDefault(instanceLocation, Collections.emptyMap())
            .values()
            .forEach(v -> v.entrySet().removeIf(pred));
      } else {
        annotations.getOrDefault(instanceLocation, Collections.emptyMap())
            .values()
            .forEach(m -> {
              m.entrySet().stream()
                  .filter(pred)
                  .forEach(e -> e.getValue().setValid(false));
            });
      }
    }

    // Remove all failing results for this instance location and below
    if (result && errors != null) {
      errors.entrySet().stream()
          .filter(e -> e.getKey().startsWith(instanceLocation))
          .forEach(e -> {
            e.getValue().values().stream()
                .filter(err -> !err.result &&
                               err.loc.keyword.startsWith(keywordLocation))
                .forEach(a -> a.setPruned(true));
          });
    }

    state = parentState;
    return result;
  }

  /**
   * Applies a keyword to the given schema and instance.
   *
   * @param k the keyword
   * @param schema the schema
   * @param instance the instance
   * @return the validation result.
   * @throws MalformedSchemaException if the schema is not valid.
   */
  public boolean applyKeyword(Keyword k, JsonElement schema, JsonElement instance)
      throws MalformedSchemaException {
    state.loc = new Locator(state.loc.instance,
                            resolvePointer(state.keywordParentLocation, k.name()),
                            resolveAbsolute(state.absKeywordParentLocation, k.name()));

    // Copy the keyword state in case it changes underfoot
    Locator loc = state.loc;
    boolean isCollectSubAnnotations = state.isCollectSubAnnotations;

    boolean result = true;
    String msg = null;

    if (!k.apply(schema, instance, state.schemaObject, this)) {
      result = false;
      msg = "\"" + k.name() + "\" didn't validate";
    }

    // Restore the keyword state that may have changed underfoot
    state.loc = loc;
    state.isCollectSubAnnotations = isCollectSubAnnotations;

    if (!hasError()) {
      addError(result, msg);
    }

    return result;
  }
}
