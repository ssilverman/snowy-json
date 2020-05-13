/*
 * Created by shawn on 4/29/20 12:49 AM.
 */
package com.qindesign.json.schema;

import static com.qindesign.json.schema.Validator.ANCHOR_PATTERN;

import com.google.common.reflect.ClassPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.qindesign.json.schema.keywords.CoreId;
import com.qindesign.json.schema.keywords.CoreRef;
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
      Set.of("$schema"),
      Set.of("$id"),
      Set.of(
          "$recursiveAnchor",
          "$anchor",
          "$vocabulary"),
      EVERY_OTHER_KEYWORD,
      Set.of(
          "additionalItems",
          "additionalProperties",
          "maxContains",
          "minContains"),
      Set.of(
          "unevaluatedItems",
          "unevaluatedProperties"));

  private static final Map<String, Keyword> keywords = new HashMap<>();
  private static final Map<String, Integer> keywordClasses;

  /**
   * Tracks context state.
   */
  private static final class State implements Cloneable {
    private State() {
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

    @Override
    protected Object clone() {
      try {
        return super.clone();
      } catch (CloneNotSupportedException ex) {
        logger.log(Level.SEVERE, "Unexpected", ex);
        throw new RuntimeException("Unexpected", ex);
      }
    }
  }

  static {
    // First, load all the known keywords
    try {
      findKeywords();
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error finding keywords", ex);
    }
    // The 'keywords' set now contains all the keywords

    // Temporary tuple type
    final class Pair<K, V> {
      K key;
      V value;

      Pair(K key, V value) {
        this.key = key;
        this.value = value;
      }
    }

    // Fun with streams
    // Map each keyword to its "class number", so we can sort easily
    keywordClasses = IntStream.range(0, KEYWORD_SETS.size())
        .boxed()
        .flatMap(i -> KEYWORD_SETS.get(i).stream().map(name -> new Pair<>(name, i)))
        .collect(Collectors.toMap(p -> p.key, p -> p.value));
    int otherClass = KEYWORD_SETS.indexOf(EVERY_OTHER_KEYWORD);
    keywords.keySet().forEach(name -> keywordClasses.putIfAbsent(name, otherClass));
  }

  /**
   * Finds all the keyword implementations.
   */
  @SuppressWarnings("UnstableApiUsage")
  private static void findKeywords() throws IOException {
    ClassPath classPath = ClassPath.from(CLASS.getClassLoader());
    Set<ClassPath.ClassInfo> classes =
        classPath.getTopLevelClasses(CLASS.getPackage().getName() + ".keywords");
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
          logger.config("Keyword: " + keyword.name());
        }
      } catch (ReflectiveOperationException | RuntimeException ex) {
        logger.log(Level.SEVERE, "Error loading keyword: " + c, ex);
        Throwable cause = ex.getCause();
        if (cause != null) {
          cause.printStackTrace();
        }
      }
    }
  }

  /** Stores the validation options. */
  private Options options = new Options();

  /** Vocabularies in use. */
  private final Map<URI, Boolean> vocabularies = new HashMap<>();

  /**
   * Annotations collection, maps element location to its annotations:<br>
   * instance location -> name -> schema location -> value
   */
  private final Map<String, Map<String, Map<String, Annotation>>> annotations = new HashMap<>();

  /**
   * The initial base URI passed in with the constructor. This may or may not
   * match the document ID.
   */
  private final URI baseURI;

  /** The current processing state. */
  private State state;

  private final Map<Id, JsonElement> knownIDs;
  private final Map<URI, URL> knownURLs;

  /**
   * Tracks schemas that have either been validated or in the process of being
   * validated, to avoid $schema recursion.
   * <p>
   * This being non-empty is a good indicator that this is a meta-schema.
   */
  private final Set<URI> validatedSchemas;

  /**
   * Creates a new schema context. Given is an absolute URI from where the
   * schema was obtained. The URI will be normalized.
   * <p>
   * Only empty fragments are allowed in the base URI, if present.
   *
   * @param baseURI the initial base URI
   * @param spec the specification to use
   * @param knownIDs the known IDs in this resource
   * @param knownURLs known URLs mapped from IDs
   * @param validatedSchemas the set of validated schemas
   * @throws IllegalArgumentException if the base URI is not absolute or if it
   *         has a non-empty fragment.
   * @throws NullPointerException if any of the arguments are {@code null}.
   */
  public ValidatorContext(URI baseURI, Specification spec,
                          Map<Id, JsonElement> knownIDs, Map<URI, URL> knownURLs,
                          Set<URI> validatedSchemas) {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(knownIDs, "knownIDs");
    Objects.requireNonNull(knownURLs, "knownURLs");
    Objects.requireNonNull(validatedSchemas, "validatedSchemas");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }
    if (Validator.hasNonEmptyFragment(baseURI)) {
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
    this.knownIDs = Collections.unmodifiableMap(knownIDs);
    this.knownURLs = Collections.unmodifiableMap(knownURLs);
    this.validatedSchemas = Collections.unmodifiableSet(validatedSchemas);

    state = new State();
    state.baseURI = baseURI;
    state.spec = spec;
    state.prevRecursiveBaseURI = null;
    state.recursiveBaseURI = null;
    state.schemaObject = null;
    state.isRoot = true;
    state.keywordParentLocation = null;
    state.keywordLocation = "";
    state.absKeywordLocation = baseURI;
    state.instanceLocation = "";
  }

  /**
   * Returns all the options. Use this to modify or retrieve any options.
   *
   * @return all the options.
   * @link Options
   */
  public Options options() {
    return options;
  }

  /**
   * Returns all the known resources.
   */
  public Map<URI, URL> knownURLs() {
    return knownURLs;
  }

  /**
   * Returns the set of schemas that have already been validated or are in the
   * process of being validated. The set will be unmodifiable.
   *
   * @return the set of validated schemas.
   */
  public Set<URI> validatedSchemas() {
    return validatedSchemas;
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
    state.absKeywordLocation = state.baseURI;
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
   * Returns the parent object of the current keyword.
   */
  public JsonObject parentObject() {
    return state.schemaObject;
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
   * Finds the element associated with the given ID. If there is no such element
   * having the ID then this returns {@code null}. If the returned element was
   * from a new resource and is a schema then the current state will be set as
   * the root.
   * <p>
   * This first tries locally and then tries from a list of known resources.
   * <p>
   * If the ID has a fragment then it will be removed prior to searching the
   * other known resources.
   *
   * @param id the id
   * @return the element having the given ID or {@code null} if there's no
   *         such element.
   */
  public JsonElement findAndSetRoot(URI id) {
    JsonElement e = knownIDs.get(new Id(id));
    if (e != null) {
      return e;
    }

    // Strip off the fragment, but after we know we don't know about it
    id = Validator.stripFragment(id);

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
          return Main.parse(in);
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
   * Adds an annotation to the current instance location.
   *
   * @param name the annotation name
   * @param value the annotation value
   */
  public void addAnnotation(String name, Object value) {
    Annotation a = new Annotation(name);
    a.instanceLocation = state.instanceLocation;
    a.keywordLocation = state.keywordLocation;
    a.absKeywordLocation = state.absKeywordLocation;
    a.value = value;

    var aForInstance = annotations.computeIfAbsent(state.instanceLocation, k -> new HashMap<>());
    var aForName = aForInstance.computeIfAbsent(name, k -> new HashMap<>());
    aForName.put(state.keywordLocation, a);
  }

  /**
   * Returns whether there's an existing annotation having the given name at the
   * current instance location.
   *
   * @param name the annotation name
   * @return whether there's an existing annotation.
   */
  public boolean hasAnnotation(String name) {
    return annotations
        .getOrDefault(state.instanceLocation, Collections.emptyMap())
        .getOrDefault(name, Collections.emptyMap())
        .containsKey(state.keywordLocation);
  }

  /**
   * Gets the all the annotations attached to the current instance location for
   * the given name.
   *
   * @param name the annotation name
   * @return a map keyed by schema location
   */
  public Map<String, Annotation> getAnnotations(String name) {
    Map<String, Map<String, Annotation>> m =
        annotations.getOrDefault(state.instanceLocation, Collections.emptyMap());
    return Collections.unmodifiableMap(m.getOrDefault(name, Collections.emptyMap()));
  }

  /**
   * Merges the path with the base URI. If the given path is empty, this returns
   * the base URI. It is assumed that the base contains an absolute part and a
   * a JSON pointer part in the fragment.
   *
   * @param base the base URI
   * @param path path to append
   * @return the merged URI.
   */
  private static URI resolveAbsolute(URI base, String path) {
    if (path.isEmpty()) {
      return base;
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
   * JSON pointer.
   * <p>
   * This also ensures that the path is a valid JSON pointer by escaping any
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
   * Checks whether the given JSON element is a valid JSON schema. If it is not
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
      throw new MalformedSchemaException("not a valid JSON schema",
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
   * Follows a JSON pointer into a JSON element and returns the requested
   * sub-element. It is expected that {@code ptr} is a valid JSON pointer.
   * <p>
   * This sets the base URI along the way as it passes each $id, starting from
   * the given base URI. This ignores any child $id at the root level because
   * that's how we got here in the first place; we don't need to process
   * it again.
   *
   * @param baseURI the starting point for any new base URI
   * @param e the element to traverse
   * @param ptr the JSON pointer
   * @return the specified sub-element or {@code null} if not found.
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

      JsonElement id = e.getAsJsonObject().get(CoreId.NAME);
      if (i > 1 && id != null && Validator.isString(id)) {
        URI uri;
        try {
          uri = new URI(id.getAsString()).normalize();
        } catch (URISyntaxException ex) {
          schemaError("not a valid URI-reference", path.toString() + "/" + CoreId.NAME);
          return null;
        }

        if (Validator.hasNonEmptyFragment(uri)) {
          if (specification().ordinal() >= Specification.DRAFT_2019_09.ordinal()) {
            schemaError("has a non-empty fragment", path.toString() + "/" + CoreId.NAME);
            return null;
          }
          if (!ANCHOR_PATTERN.matcher(uri.getRawFragment()).matches()) {
            schemaError("invalid plain name", path.toString() + "/" + CoreId.NAME);
            return null;
          }

          // If it's not just a fragment then it represents a new base URI
          if (uri.getScheme() != null || !uri.getRawSchemeSpecificPart().isEmpty()) {
            newBase = newBase.resolve(uri);
          }
        } else {
          newBase = newBase.resolve(Validator.stripFragment(uri));
        }
      }

      // Transform the part
      part = part.replace("~0", "~");
      part = part.replace("~1", "/");
      e = e.getAsJsonObject().get(part);
    }
    if (e != null) {
      state.baseURI = newBase;
      state.absKeywordLocation = newBase;
    }
    return e;
  }

  /**
   * Applies a schema to the given instance. The schema and instance path
   * parameters are the relative element name, either a name or a number. An
   * empty string means the current location.
   * <p>
   * This first checks that the schema is valid. A valid schema is either an
   * object or a Boolean.
   *
   * @param schema the schema, an object or a Boolean
   * @param schemaPath the schema path
   * @param instance the instance element
   * @param instancePath the instance path
   */
  public boolean apply(JsonElement schema, String schemaPath,
                       JsonElement instance, String instancePath)
      throws MalformedSchemaException
  {
    if (Validator.isBoolean(schema)) {
      boolean result = schema.getAsBoolean();
      addAnnotation("error", new ValidationResult(result, null));
      return result;
    }

    URI absKeywordLocation = resolveAbsolute(state.absKeywordLocation, schemaPath);
    if (!schema.isJsonObject()) {
      throw new MalformedSchemaException("not a valid JSON schema", absKeywordLocation);
    }

    JsonObject schemaObject = schema.getAsJsonObject();
    if (schemaObject.size() == 0) {  // Empty schemas always validate
      addAnnotation("error", new ValidationResult(true, null));
      return true;
    }

    String keywordLocation = resolvePointer(state.keywordLocation, schemaPath);
    String instanceLocation = resolvePointer(state.instanceLocation, instancePath);

    State parentState = state;
    state = (State) state.clone();
    assert state != null;
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
      if (!k.apply(m.getValue(), instance, this)) {
        // Remove all subschema annotations that aren't errors
        var a = annotations.get(instanceLocation);
        if (a != null) {
          for (var byName : a.entrySet()) {
            byName.getValue().entrySet()
                .removeIf(e -> !Objects.equals(e.getValue().name, "error") &&
                               e.getKey().startsWith(state.keywordLocation));
          }
        }
        if (!hasAnnotation("error")) {
          addAnnotation("error", new ValidationResult(false, k.name()));
        }

        // Don't escape early because we need to process all the keywords
        result = false;
      }
    }

    state = parentState;
    if (result) {
      addAnnotation("error", new ValidationResult(true, null));
    }
    return result;
  }
}
