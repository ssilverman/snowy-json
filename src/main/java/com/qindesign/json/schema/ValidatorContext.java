/*
 * Created by shawn on 4/29/20 12:49 AM.
 */
package com.qindesign.json.schema;

import com.google.common.reflect.ClassPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  private static final Map<String, Keyword> keywords = new HashMap<>();

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

  private final URI baseURI;
  private State state;

  private final Map<Id, JsonElement> knownIDs;

  /**
   * Creates a new schema context. Given is an absolute URI from where the
   * schema was obtained. The URI will be normalized.
   *
   * @param baseURI the initial base URI
   * @throws IllegalArgumentException if the base URI is not absolute.
   */
  public ValidatorContext(URI baseURI, Map<Id, JsonElement> knownIDs) {
    Objects.requireNonNull(baseURI, "baseURI");
    Objects.requireNonNull(knownIDs, "knownIDs");

    if (!baseURI.isAbsolute()) {
      throw new IllegalArgumentException("baseURI must be absolute");
    }

    this.baseURI = baseURI.normalize();
    this.knownIDs = knownIDs;

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
   */
  public URI schemaParentLocation() {
    return state.keywordParentLocation;
  }

  /**
   * Returns the location of the current keyword.
   */
  public URI schemaLocation() {
    return state.keywordLocation;
  }

  /**
   * Finds the element associated with the given ID. If there is no such element
   * having the ID then this returns {@code null}.
   *
   * @param id the id
   * @return the element having the given ID or {@code null} if there's no
   *         such element.
   */
  public JsonElement find(URI id) {
    return knownIDs.get(new Id(id));
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
   * the given name.
   *
   * @param name the annotation name
   * @return a map keyed by schema location
   */
  public Map<URI, Annotation> getAnnotations(String name) {
    Map<String, Map<URI, Annotation>> m = annotations.getOrDefault(state.instanceLocation,
                                                                   Collections.emptyMap());
    return m.getOrDefault(name, Collections.emptyMap());
  }

  /**
   * Merges the path with the base URI. If the given path is empty, this returns
   * the base URI.
   *
   * @param base the base URI
   * @param path the possibly empty path to append
   * @return the merged URI.
   */
  private static URI resolve(URI base, String path) {
    if (path.isEmpty()) {
      return base;
    }
    if (path.equals("/")) {
      return base.resolve("/");  // Normalize?
    }
    return base.resolve(base.getPath() + "/" + path);  // Normalize?
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
    throw new MalformedSchemaException(err, resolve(state.absKeywordLocation, path));
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
                                         resolve(state.absKeywordLocation, path));
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

    URI absKeywordLocation = resolve(state.absKeywordLocation, schemaPath);
    if (!schema.isJsonObject()) {
      throw new MalformedSchemaException("not a valid JSON schema", absKeywordLocation);
    }

    JsonObject schemaObject = schema.getAsJsonObject();
    if (schemaObject.size() == 0) {  // Empty schemas always validate
      return true;
    }

    state.isRoot = (state.schemaObject == null);
    state.schemaObject = schemaObject;
    URI keywordLocation = resolve(state.keywordLocation, schemaPath);
    URI instanceLocation = resolve(state.instanceLocation, instancePath);

    State parentState = state;
    state = (State) state.clone();
    assert state != null;
    state.keywordParentLocation = keywordLocation;
    state.instanceLocation = instanceLocation;

    try {
      for (String name : FIRST_KEYWORDS) {
        JsonElement e = schemaObject.get(name);
        if (e == null) {
          continue;
        }
        Keyword k = keywords.get(name);
        if (k == null) {
          continue;
        }

        state.keywordLocation = resolve(keywordLocation, name);
        state.absKeywordLocation = resolve(absKeywordLocation, name);
        if (!k.apply(e, instance, this)) {
          return false;
        }
      }

      for (var e : schemaObject.entrySet()) {
        if (DEPENDENT_KEYWORDS.contains(e.getKey()) || FIRST_KEYWORDS.contains(e.getKey())) {
          continue;
        }
        Keyword k = keywords.get(e.getKey());
        if (k == null) {
          continue;
        }

        state.keywordLocation = resolve(keywordLocation, e.getKey());
        state.absKeywordLocation = resolve(absKeywordLocation, e.getKey());
        if (!k.apply(e.getValue(), instance, this)) {
          return false;
        }
      }

      return true;

      // TODO: Process dependent keywords
    } finally {
      state = parentState;
    }
  }
}
