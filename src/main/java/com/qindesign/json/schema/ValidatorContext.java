/*
 * Created by shawn on 4/29/20 12:49 AM.
 */
package com.qindesign.json.schema;

import com.google.common.reflect.ClassPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The schema processing state.
 */
public final class ValidatorContext {
  private static final Class<?> CLASS = ValidatorContext.class;
  private static final Logger logger = Logger.getLogger(CLASS.getName());

  /** These core keywords are processed first, in this order. */
  private static final Set<String> FIRST_KEYWORDS = new LinkedHashSet<>(
      Stream.of(
          "$id",
          "$recursiveAnchor",
          "$schema",
          "$anchor",
          "$vocabulary",
          "$defs"
      ).collect(Collectors.toList()));

  /** These keywords depend on others before they can be applied. */
  private static final Set<String> DEPENDENT_KEYWORDS =
      Stream.of(
          "if",
          "then",
          "else",
          "additionalItems",
          "unevaluatedItems",
          "additionalProperties",
          "unevaluatedProperties",
          "maxContains",
          "minContains"
      ).collect(Collectors.toSet());

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

    /** The previous $recursiveAnchor=true base. */
    URI prevRecursiveBaseURI;

    /** Any $recursiveAnchor=true bases we find along the way. */
    URI recursiveBaseURI;

    URI keywordParentLocation;
    URI keywordLocation;
    URI absKeywordLocation;
    URI instanceLocation;

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
    try {
      findKeywords();
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error finding keywords", ex);
    }

    // Fun with streams
    // Map each keyword to its "class number", so we can sort easily
    keywordClasses = keywords.keySet().stream().collect(Collectors.toMap(
        Function.identity(),
        name -> {
          if (FIRST_KEYWORDS.contains(name)) {
            return 0;
          }
          if (DEPENDENT_KEYWORDS.contains(name)) {
            return 2;
          }
          return 1;
        }));
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

  /** Vocabularies in use. */
  private final Map<URI, Boolean> vocabularies = new HashMap<>();

  /** Annotations collection, maps element location to its annotations. */
  private final Map<URI, Map<String, Map<URI, Annotation>>> annotations = new HashMap<>();

  /**
   * The initial base URI passed in with the constructor. This may or may not
   * match the document ID.
   */
  private final URI baseURI;

  /** The current processing state. */
  private State state;

  private final Map<Id, JsonElement> knownIDs;

  /**
   * Tracks schemas that have either been validated or in the process of being
   * validated, to avoid $schema recursion.
   */
  private final Set<URI> validatedSchemas;

  /**
   * Creates a new schema context. Given is an absolute URI from where the
   * schema was obtained. The URI will be normalized.
   * <p>
   * Only empty fragments are allowed in the base URI, if present.
   *
   * @param baseURI the initial base URI
   * @param knownIDs the known IDs in this resource
   * @param validatedSchemas the list of validated schemas, must be mutable
   * @throws IllegalArgumentException if the base URI is not absolute or if it
   *         has a non-empty fragment.
   * @throws NullPointerException if any of the arguments are {@code null}.
   */
  public ValidatorContext(URI baseURI, Map<Id, JsonElement> knownIDs, Set<URI> validatedSchemas) {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(knownIDs, "knownIDs");
    Objects.requireNonNull(validatedSchemas, "validatedSchemas");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }
    if (baseURI.getRawFragment() != null) {
      if (!baseURI.getRawFragment().isEmpty()) {
        throw new IllegalArgumentException("baseURI has a non-empty fragment");
      }
    } else {
      // Ensure there's an empty fragment
      try {
        baseURI = new URI(baseURI.getScheme(), baseURI.getRawSchemeSpecificPart(), "");
      } catch (URISyntaxException ex) {
        throw new IllegalArgumentException("Unexpected bad base URI");
      }
    }

    this.baseURI = baseURI.normalize();
    this.knownIDs = knownIDs;
    this.validatedSchemas = Collections.unmodifiableSet(validatedSchemas);

    state = new State();
    state.baseURI = baseURI;
    state.prevRecursiveBaseURI = null;
    state.recursiveBaseURI = null;
    state.schemaObject = null;
    state.keywordParentLocation = null;
    state.keywordLocation = URI.create("");
    state.absKeywordLocation = baseURI;
    state.instanceLocation = URI.create("");
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
   * Sets the base URI by normalizing the given URI with the current base URI.
   * This does not change the recursive base URI.
   *
   * @param uri the new relative base URI
   */
  public void setBaseURI(URI uri) {
    state.baseURI = state.baseURI.resolve(uri);
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
   * Enables or disables a vocabulary. This returns whether the ID is unique.
   *
   * @param id the vocabulary
   * @param flag whether to enable or disable the vocabulary
   */
  public boolean setVocabulary(URI id, boolean flag) {
    return vocabularies.putIfAbsent(id, flag) == null;
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
  public URI schemaParentLocation() {
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
  public URI schemaLocation() {
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
   * If the ID has a fragment then it will be removed prior to searching.
   *
   * @param id the id
   * @return the element having the given ID or {@code null} if there's no
   *         such element.
   */
  public JsonElement findAndSetRoot(URI id) {
    if (id.getRawFragment() != null) {
      try {
        id = new URI(id.getScheme(), id.getRawSchemeSpecificPart(), null);
      } catch (URISyntaxException ex) {
        logger.log(Level.SEVERE, "Unexpected bad URI", ex);
        return null;
      }
    }
    JsonElement e = knownIDs.get(new Id(id));
    if (e == null) {
      e = Validator.loadResource(id);
      if (e != null && Validator.isSchema(e)) {
        state.schemaObject = null;
      }
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
    Annotation a = new Annotation();
    a.name = name;
    a.instanceLocation = state.instanceLocation;
    a.schemaLocation = state.keywordLocation;
    a.value = value;

    var aForInstance = annotations.computeIfAbsent(state.instanceLocation, k -> new HashMap<>());
    var aForName = aForInstance.computeIfAbsent(name, k -> new HashMap<>());
    aForName.put(state.keywordLocation, a);
  }

  /**
   * Gets the all the annotations attached to the current instance location for
   * the given name. The returned map will be unmodifiable.
   *
   * @param name the annotation name
   * @return a map keyed by schema location
   */
  public Map<URI, Annotation> getAnnotations(String name) {
    Map<String, Map<URI, Annotation>> m = annotations.getOrDefault(state.instanceLocation,
                                                                   Collections.emptyMap());
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
    return base.resolve("#" + base.getRawFragment() + "/" + path);
  }

  /**
   * Merges the path with the base URI. If the given path is empty, this returns
   * the base URI. It is assumed that the base path is a JSON pointer.
   *
   * @param base the base URI
   * @param path path to append
   * @return the merged URI.
   */
  private static URI resolvePointer(URI base, String path) {
    if (path.isEmpty()) {
      return base;
    }
    return base.resolve(base.getPath() + "/" + path);
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
      return schema.getAsBoolean();
    }

    URI absKeywordLocation = resolveAbsolute(state.absKeywordLocation, schemaPath);
    if (!schema.isJsonObject()) {
      throw new MalformedSchemaException("not a valid JSON schema", absKeywordLocation);
    }

    JsonObject schemaObject = schema.getAsJsonObject();
    if (schemaObject.size() == 0) {  // Empty schemas always validate
      return true;
    }

    state.isRoot = (state.schemaObject == null);
    state.schemaObject = schemaObject;
    URI keywordLocation = resolvePointer(state.keywordLocation, schemaPath);
    URI instanceLocation = resolvePointer(state.instanceLocation, instancePath);

    State parentState = state;
    state = (State) state.clone();
    assert state != null;
    state.keywordParentLocation = keywordLocation;
    state.instanceLocation = instanceLocation;

    // Sort the names in the schema by their required evaluation order
    // Also, more fun with streams
    var ordered = schemaObject.entrySet().stream()
        .filter(e -> keywords.containsKey(e.getKey()))
        .sorted(Comparator.comparing(e -> keywordClasses.get(e.getKey())))
        .collect(Collectors.toList());

    try {
      for (var e : ordered) {
        Keyword k = keywords.get(e.getKey());
        state.keywordLocation = resolvePointer(keywordLocation, e.getKey());
        state.absKeywordLocation = resolveAbsolute(absKeywordLocation, e.getKey());
        if (!k.apply(e.getValue(), instance, this)) {
          return false;
        }
      }

      return true;
    } finally {
      state = parentState;
    }
  }
}
