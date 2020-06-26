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
 * Created by shawn on 5/30/20 10:51 PM.
 */
package com.qindesign.net;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * A URI parser that follows RFC 3986. Successfully parsing a URI will result in
 * the following 5 components:
 * <ol>
 * <li>Scheme</li>
 * <li>Authority</li>
 * <li>Path</li>
 * <li>Query</li>
 * <li>Fragment</li>
 * </ol>
 * <p>
 * If there is an <em>authority</em> component then the following may also be
 * set. These are subcomponents of <em>authority</em>.
 * <ol>
 * <li>User info</li>
 * <li>Host</li>
 * <li>Port</li>
 * </ol>
 * <p>
 * This parser assumes that all percent-encoded values are encoded in UTF-8.
 * <p>
 * After parsing, the following points hold true:
 * <ul>
 * <li>The <em>path</em> component will never be {@code null}, but it may
 *     be empty.</li>
 * <li>If the <em>authority</em> component is not {@code null} then the
 *     <em>host</em> subcomponent will not be {@code null}. The converse is also
 *     true: if the <em>host</em> subcomponent is not {@code null} then the
 *     <em>authority</em> component will not be {@code null}.</li>
 * <li>If the <em>user info</em> subcomponent is not {@code null} then the
 *     <em>authority</em> component will not be {@code null}. The converse is
 *     not true.</li>
 * <li>If the <em>port</em> subcomponent is non-negative then the
 *     <em>authority</em> component will not be {@code null}. The converse is
 *     not true.</li>
 * <li>All the other components, <em>scheme</em>, <em>query</em>, and
 *     <em>fragment</em>, and the <em>user info</em> subcomponent, may be
 *     {@code null}. The <em>port</em> subcomponent may be -1.</li>
 * </ul>
 * <p>
 * The following concepts are also defined:
 * <ul>
 * <li>A "raw component" contains percent-encoded characters. These are
 *     "encoded strings".
 * <li>A "decoded string" is a string with no percent-encoded characters. Any of
 *     these will have already been decoded.</li>
 * <li>An "encoded string" potentially contains percent-encoded characters. The
 *     "raw components" are "encoded strings".</li>
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html">RFC 3986: Uniform Resource Identifier (URI): Generic Syntax</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3629">RFC 3629: UTF-8, a transformation format of ISO 10646</a>
 */
public class URI implements Comparable<URI> {
  public static void main(String[] args) throws URISyntaxException {
    URI uri = URI.parse("http://a/b/c/d;p?q");
    System.out.println(uri.resolve(URI.parse("g:h")));
  }

  /** Thread-local cached UTF-8 decoder. */
  private static final ThreadLocal<CharsetDecoder> decoder =
      ThreadLocal.withInitial(StandardCharsets.UTF_8::newDecoder);

  /** Thread-local cached UTF-8 encoder. */
  private static final ThreadLocal<CharsetEncoder> encoder =
      ThreadLocal.withInitial(StandardCharsets.UTF_8::newEncoder);

  /** Hex digits for percent-encoding. */
  private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  /**
   * Parses a URI according to RFC 3986.
   *
   * @param s the string to parse, encoded
   * @return the parsed URI.
   * @throws URISyntaxException if there was a parsing error.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html">RFC 3986: Uniform Resource Identifier (URI): Generic Syntax</a>
   */
  public static URI parse(String s) throws URISyntaxException {
    return new URIParser(s).parse();
  }

  /**
   * Parses a URI and throws {@link IllegalArgumentException} if there was a
   * parsing error. This calls {@link #parse(String)} and wraps any
   * thrown {@link URISyntaxException} in an {@link IllegalArgumentException}.
   *
   * @param s the string to parse, encoded
   * @return the parsed URI.
   * @throws IllegalArgumentException if there was a parsing error.
   * @see #parse(String)
   */
  public static URI parseUnchecked(String s) {
    try {
      return parse(s);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  /**
   * Converts a hex character to its value.
   *
   * @param c the hex character
   * @return the value.
   * @throws IllegalArgumentException if the character is not a hex digit.
   */
  private static int fromHex(char c) {
    if ('0' <= c && c <= '9') {
      return c - '0';
    }
    if ('A' <= c && c <= 'F') {
      return c - 'A' + 10;
    }
    if ('a' <= c && c <= 'f') {
      return c - 'a' + 10;
    }
    throw new IllegalArgumentException("Not a hex digit");
  }

  /**
   * Decodes a percent-encoded string.
   *
   * @param s the string to decode
   * @return the decoded string.
   * @throws IllegalArgumentException if the string is not a valid
   *         percent-encoded UTF-8-encoded string.
   */
  private static String pctDecode(String s) {
    if (s == null || s.isEmpty() || s.indexOf('%') < 0) {
      return s;
    }

    int n = s.length();
    StringBuilder sb = new StringBuilder(n);
    ByteBuffer bb = ByteBuffer.allocate(n);
    CharBuffer cb = CharBuffer.allocate(n);
    CharsetDecoder dec = decoder.get()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

    for (int i = 0; i < n; ) {
      char c = s.charAt(i);
      if (c != '%') {
        sb.append(c);
        i++;
        continue;
      }

      // Keep reading percent-encoded characters until no more
      bb.clear();
      do {
        if (i + 2 >= n) {
          throw new IllegalArgumentException("Bad percent-encoding");
        }
        i++;

        bb.put((byte) ((fromHex(s.charAt(i++)) << 4) | fromHex(s.charAt(i++))));
      } while (i < n && s.charAt(i) == '%');

      bb.flip();
      cb.clear();
      dec.reset();
      CoderResult cr = dec.decode(bb, cb, true);
      if (!cr.isUnderflow()) {
        throw new IllegalArgumentException("Bad UTF-8");
      }
      cr = dec.flush(cb);
      if (!cr.isUnderflow()) {
        throw new IllegalArgumentException("Bad UTF-8");
      }
      sb.append(cb.flip().toString());
    }

    return sb.toString();
  }

  /**
   * Encodes a string by percent-encoding everything not in the {@code notThese}
   * set. This also considers '%' to never be in that set.
   *
   * @param s the string to encode
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @param notThese the characters not to encode
   * @return the encoded string.
   * @throws IllegalArgumentException if there was a character
   *         encoding exception.
   */
  private static String pctEncode(String s, int start, int end, BitSet notThese) {
    StringBuilder sb = null;  // Stay null as long as
    char[] ca = new char[2];

    int charCount;
    for (int i = start; i < end; i += charCount) {
      int c = s.codePointAt(i);
      charCount = Character.charCount(c);

      if (c < 128) {
        if (c != '%' && notThese.get(c)) {
          if (sb != null) {
            sb.append((char) c);
          }
          continue;
        }
        if (sb == null) {
          sb = new StringBuilder();
          sb.append(s, 0, i);
        }
        sb.append('%').append(HEX_DIGITS[c >> 4]).append(HEX_DIGITS[c & 0x0f]);
        continue;
      }

      ca[0] = s.charAt(i);
      if (charCount == 2) {
        ca[1] = s.charAt(i + 1);
      }
      try {
        ByteBuffer bb = encoder.get().encode(CharBuffer.wrap(ca, 0, charCount));
        while (bb.hasRemaining()) {
          if (sb == null) {
            sb = new StringBuilder();
            sb.append(s, 0, i);
          }
          int b = bb.get() & 0xff;
          sb.append('%').append(HEX_DIGITS[b >> 4]).append(HEX_DIGITS[b & 0x0f]);
        }
      } catch (CharacterCodingException ex) {
        throw new IllegalArgumentException(ex);
      }
    }

    if (sb == null) {
      return s.substring(start, end);
    }
    return sb.toString();
  }

  private final String scheme;
  private final String authority;
  private String decodedAuthority;
  private final String path;
  private String decodedPath;
  private final String query;
  private String decodedQuery;
  private final String fragment;
  private String decodedFragment;

  // Parts of authority, not set if authority not set
  private final String userInfo;
  private String decodedUserInfo;
  private final String host;
  private String decodedHost;
  private final int port;

  // Cached strings
  private String string;
  private String decodedString;
  private String caseNormalizedString;

  /**
   * Creates a new URI and parses and validates all the components. This first
   * percent-encodes all percent-encodable parts and then parses the value
   * as usual. In other words, all arguments are expected to be decoded strings.
   * <p>
   * Note that all "%" characters will be encoded into "%25".
   *
   * @param scheme the scheme
   * @param authority the authority
   * @param path the path
   * @param query the query
   * @param fragment the fragment
   * @throws URISyntaxException if there was a parsing error.
   */
  public URI(String scheme, String authority, String path, String query, String fragment)
      throws URISyntaxException {
    URIParser p = new URIParser(toEncodedString(scheme, authority, path, query, fragment));
    p.parse();

    this.scheme = p.scheme;
    this.authority = p.authority;
    this.path = p.path;
    this.query = p.query;
    this.fragment = p.fragment;

    this.userInfo = p.userInfo;
    this.host = p.host;
    this.port = p.port;
  }

  /**
   * Creates a new URI from a {@link java.net.URI}.
   *
   * @param uri the {@link java.net.URI} instance
   * @throws NullPointerException if the argument is {@code null}.
   */
  public URI(java.net.URI uri) {
    this.scheme = uri.getScheme();
    this.authority = uri.getRawAuthority();
    this.path = Optional.ofNullable(uri.getRawPath()).orElse("");
    this.query = uri.getRawQuery();
    this.fragment = uri.getRawFragment();

    if (this.authority == null) {
      this.userInfo = null;
      this.host = null;
      this.port = -1;
    } else {
      this.userInfo = uri.getRawUserInfo();
      this.host = Optional.ofNullable(uri.getHost()).orElse("");
      this.port = uri.getPort();
    }
  }

  /**
   * Creates a URI and does no parsing, validating, or processing. All arguments
   * are expected to be in raw/encoded form.
   *
   * @param scheme the scheme
   * @param authority the authority
   * @param userInfo the user info, part of authority
   * @param host the host, part of authority
   * @param port the port, part of authority
   * @param path the path
   * @param query the query
   * @param fragment the fragment
   */
  URI(String scheme, String authority,
      String userInfo, String host, int port,
      String path, String query, String fragment) {
    this.scheme = scheme;
    this.authority = authority;
    this.userInfo = userInfo;
    this.host = host;
    this.port = port;
    this.path = path;
    this.query = query;
    this.fragment = fragment;
  }

  /**
   * Returns the <em>scheme</em> component. This may be {@code null}.
   *
   * @return the <em>scheme</em> component, may be {@code null}.
   */
  public String scheme() {
    return scheme;
  }

  /**
   * Returns the decoded <em>authority</em> component. This may be {@code null}.
   *
   * @return the decoded <em>authority</em> component, may be {@code null}.
   */
  public String authority() {
    if (decodedAuthority == null) {
      decodedAuthority = pctDecode(authority);
    }
    return decodedAuthority;
  }

  /**
   * Returns the raw <em>authority</em> component. This may be {@code null}.
   *
   * @return the raw <em>authority</em> component, may be {@code null}.
   */
  public String rawAuthority() {
    return authority;
  }

  /**
   * Returns the decoded <em>path</em> component. This will never be
   * {@code null}, but it may be empty.
   *
   * @return the decoded <em>path</em> component, never {@code null}.
   */
  public String path() {
    if (decodedPath == null) {
      decodedPath = pctDecode(path);
    }
    return decodedPath;
  }

  /**
   * Returns the raw <em>path</em> component. This will never be {@code null},
   * but it may be empty.
   *
   * @return the raw <em>path</em> component, never {@code null}.
   */
  public String rawPath() {
    return path;
  }

  /**
   * Returns the decoded <em>query</em> component. This may be {@code null}.
   *
   * @return the decoded <em>query</em> component, may be {@code null}.
   */
  public String query() {
    if (decodedQuery == null) {
      decodedQuery = pctDecode(query);
    }
    return decodedQuery;
  }

  /**
   * Returns the raw <em>query</em> component. This may be {@code null}.
   *
   * @return the raw <em>query</em> component, may be {@code null}.
   */
  public String rawQuery() {
    return query;
  }

  /**
   * Returns the decoded <em>fragment</em> component. This may be {@code null}.
   *
   * @return the decoded <em>fragment</em> component, may be {@code null}.
   */
  public String fragment() {
    if (decodedFragment == null) {
      decodedFragment = pctDecode(fragment);
    }
    return decodedFragment;
  }

  /**
   * Returns the raw <em>fragment</em> component. This may be {@code null}.
   *
   * @return the raw <em>fragment</em> component, may be {@code null}.
   */
  public String rawFragment() {
    return fragment;
  }

  /**
   * Returns the decoded <em>user info</em> authority subcomponent. This may
   * be {@code null}.
   *
   * @return the decoded <em>user info</em> authority subcomponent, may
   *         be {@code null}.
   */
  public String userInfo() {
    if (decodedUserInfo == null) {
      decodedUserInfo = pctDecode(userInfo);
    }
    return decodedUserInfo;
  }

  /**
   * Returns the raw <em>user info</em> authority subcomponent. This may
   * be {@code null}.
   *
   * @return the raw <em>user info</em> authority subcomponent, may
   *         be {@code null}.
   */
  public String rawUserInfo() {
    return userInfo;
  }

  /**
   * Returns the decoded <em>host</em> authority subcomponent. This will only be
   * {@code null} if the <em>authority</em> is {@code null}.
   *
   * @return the decoded <em>user info</em> authority subcomponent, only
   *         {@code null} if the <em>authority</em> is {@code nul}.
   */
  public String host() {
    if (decodedHost == null) {
      decodedHost = pctDecode(host);
    }
    return decodedHost;
  }

  /**
   * Returns the raw <em>host</em> authority subcomponent. This will only be
   * {@code null} if the <em>authority</em> is {@code null}.
   *
   * @return the raw <em>user info</em> authority subcomponent, only
   *         {@code null} if the <em>authority</em> is {@code nul}.
   */
  public String rawHost() {
    return host;
  }

  /**
   * Returns the <em>port</em> authority subcomponent. This may be -1.
   *
   * @return the <em>port</em> authority subcomponent, may be -1.
   */
  public int port() {
    return port;
  }

  /**
   * Returns whether this URI is absolute. A URI is absolute when its scheme is
   * not defined. Note that RFC 3986 defines an absolute URI as a URI having no
   * fragment component, but this does not check for that case.
   *
   * @return whether the URI is considered absolute.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-4.3">4.3. Absolute URI</a>
   */
  public boolean isAbsolute() {
    return scheme != null;
  }

  /**
   * Returns whether this URI is "opaque", per the older RFC 2396 definition.
   * A URI is opaque if a scheme is defined and the path is non-empty and it
   * doesn't start with a "/".
   *
   * @return whether the URI is "opaque", per RFC 2396.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc2396#section-3">3. URI Syntactic Components</a>
   * @deprecated
   */
  @Deprecated
  public boolean isOpaque() {
    return scheme != null && !path.isEmpty() && !path.startsWith("/");
  }

  /**
   * Returns whether this is a relative reference that begins with two
   * slash characters.
   *
   * @return whether this is a network-path reference.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-4.2">4.2. Relative Reference</a>
   */
  public boolean isNetworkPathReference() {
    return scheme == null && authority != null;
  }

  /**
   * Returns whether this is a relative reference that begins with a single
   * slash character.
   *
   * @return whether this is an absolute-path reference.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-4.2">4.2. Relative Reference</a>
   */
  public boolean isAbsolutePathReference() {
    return scheme == null && path.startsWith("/");
  }

  /**
   * Returns whether this is a relative reference that does not begin with a
   * single slash character.
   *
   * @return whether this is a relative-path reference.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-4.2">4.2. Relative Reference</a>
   */
  public boolean isRelativePathReference() {
    return scheme == null && !path.startsWith("/");
  }

  /**
   * Converts a URI reference that might be relative to a given base URI into a
   * target URI. This follows the steps in "5.2. Relative Resolution" of
   * RFC 3986.
   * <p>
   * Another way of saying this: Resolves the given URI against this URI.
   *
   * @param r the URI reference
   * @return the resolved URI.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2">5.2. Relative Resolution</a>
   */
  public URI resolve(URI r) {
    // [5.2.2. Transform References](https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2.2)
    if (r.scheme != null) {
      return new URI(r.scheme, r.authority, r.userInfo, r.host, r.port,
                     removeDotSegments(r.path),
                     r.query, r.fragment);
    }
    if (r.authority != null) {
      return new URI(this.scheme, r.authority, r.userInfo, r.host, r.port,
                     removeDotSegments(r.path),
                     r.query, r.fragment);
    }
    if (r.path.isEmpty()) {
      if (r.query != null) {
        return new URI(this.scheme, this.authority, this.userInfo, this.host, this.port,
                       this.path, r.query, r.fragment);
      } else {
        return new URI(this.scheme, this.authority, this.userInfo, this.host, this.port,
                       this.path, this.query, r.fragment);
      }
    }
    if (r.path.startsWith("/")) {
      return new URI(this.scheme, this.authority, this.userInfo, this.host, this.port,
                     removeDotSegments(r.path),
                     r.query, r.fragment);
    }
    return new URI(this.scheme, this.authority, this.userInfo, this.host, this.port,
                   removeDotSegments(merge(this, r.path)),
                   r.query, r.fragment);
  }

  /**
   * Routine for merging a relative-path reference with the path of the
   * base URI.
   *
   * @param base the base URI
   * @param path the relative-path reference
   * @return the merged path.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2.3">5.2.3. Merge Paths</a>
   */
  private static String merge(URI base, String path) {
    if (base.authority != null && base.path.isEmpty()) {
      return "/" + path;
    }
    int lastSlashIndex = base.path.lastIndexOf('/');
    if (lastSlashIndex >= 0) {
      return base.path.substring(0, lastSlashIndex + 1) + path;
    }
    return path;
  }

  /**
   * Routine for interpreting and removing the special "." and ".." complete
   * path segments from a referenced path. This is done after the path is
   * extracted from a reference, whether or not the path was relative, in order
   * to remove any invalid or extraneous dot-segments prior to forming the
   * target URI.
   *
   * @param path the referenced path
   * @return the processed path.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2.4">5.2.4. Remove Dot Segments</a>
   */
  private static String removeDotSegments(String path) {
    StringBuilder sb = new StringBuilder();
    int start = 0;
    int end = path.length();
    while (end > start) {
      if (end - start >= 3 && path.startsWith("../", start)) {
        // 5.2.4. Step 2.A.
        start += 3;
      } else if (end - start >= 2 && path.startsWith("./", start)) {
        // 5.2.4. Step 2.A.
        start += 2;
      } else if (end - start >= 3 && path.startsWith("/./", start)) {
        // 5.2.4. Step 2.B.
        start += 2;
      } else if (end - start == 2 && path.startsWith("/.", start)) {
        // 5.2.4. Step 2.B.
        end--;
      } else if (end - start >= 4 && path.startsWith("/../", start)) {
        // 5.2.4. Step 2.C.
        start += 3;
        int lastSlashIndex = sb.lastIndexOf("/");
        sb.setLength(Math.max(lastSlashIndex, 0));
      } else if (end - start == 3 && path.startsWith("/..", start)) {
        // 5.2.4. Step 2.C.
        end -= 2;
        int lastSlashIndex = sb.lastIndexOf("/");
        sb.setLength(Math.max(lastSlashIndex, 0));
      } else if (end - start == 1 && path.charAt(start) == '.') {
        // 5.2.4. Step 2.D.
        start++;
      } else if (end - start == 2 && path.startsWith("..", start)) {
        // 5.2.4. Step 2.D.
        start += 2;
      } else {
        // 5.2.4. Step 2.E.
        int nextSlashIndex = path.indexOf('/', start + 1);
        if (0 <= nextSlashIndex && nextSlashIndex < end) {
          // Has next "/"
          sb.append(path, start, nextSlashIndex);
          start = nextSlashIndex;
        } else {
          // No next "/"
          sb.append(path, start, end);
          start = end;
        }
      }
    }
    return sb.toString();
  }

  /**
   * Relativizes the given URI against this URI. This is the inverse
   * of resolution.
   *
   * @param r the URI reference
   * @return the relativized URI.
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2">5.2. Relative Resolution</a>
   * @see #resolve(URI)
   */
  public URI relativize(URI r) {
    if (!equalsIgnoreCase(this.scheme, r.scheme) || !Objects.equals(this.authority, r.authority)) {
      return r;
    }

    String thisPath = removeDotSegments(this.path);
    String rPath = removeDotSegments(r.path);
    if (!thisPath.equals(rPath)) {
      if (!thisPath.endsWith("/")) {
        thisPath += "/";
      }
      if (!rPath.startsWith(thisPath)) {
        return r;
      }
    }

    return new URI(null, null, null, null, -1,
                   rPath.substring(thisPath.length()),
                   r.query, r.fragment);
  }

  /**
   * Normalizes URI using these steps:
   * <ol>
   * <li>Lower-case any scheme and host</li>
   * <li>Remove any dot segments from the path</li>
   * </ol>
   * <p>
   * This may return {@code this} if the resulting URI would be the same.
   *
   * @return the normalized URI.
   */
  public URI normalize() {
    String scheme = Optional.ofNullable(this.scheme).map(String::toLowerCase).orElse(null);
    String host = Optional.ofNullable(this.host).map(String::toLowerCase).orElse(null);
    String path = removeDotSegments(this.path);

    if (Objects.equals(scheme, this.scheme) &&
        Objects.equals(host, this.host) &&
        Objects.equals(path, this.path)) {
      return this;
    }

    return new URI(scheme, authority, userInfo, host, port,
                   removeDotSegments(path),
                   query, fragment);
  }

  /**
   * Returns a {@link URL} built from this URI. This first checks if
   * there is a scheme because the URL must have a "protocol".
   * <p>
   * This is equivalent to calling {@code new URL(this.toString())} after first
   * checking that there's a scheme.
   *
   * @return a {@link URL} built from this URI.
   * @throws IllegalArgumentException if this URI has no scheme.
   * @throws MalformedURLException if there was an error building the URL.
   */
  public URL toURL() throws MalformedURLException {
    if (scheme == null) {
      throw new IllegalArgumentException("URI has no scheme");
    }
    return new URL(toString());
  }

  /**
   * Compares this URI to another.
   *
   * @param o the object to which to compare
   * @return the comparison result.
   * @see Comparable#compareTo(Object)
   */
  @Override
  public int compareTo(URI o) {
    int c;

    if ((c = compareIgnoreCase(scheme(), o.scheme())) != 0) {
      return c;
    }

    // Sort opaque URIs first
    if (isOpaque()) {
      if (o.isOpaque()) {
        if ((c = compare(path(), o.path())) != 0) {
          return c;
        }
        if ((c = compare(query(), o.query())) != 0) {
          return c;
        }
        return compare(fragment(), o.fragment());
      } else {
        return 1;
      }
    } else if (o.isOpaque()) {
      return -1;
    }

    if (authority != null && o.authority != null) {
      if ((c = compare(userInfo(), o.userInfo())) != 0) {
        return c;
      }
      if ((c = compareIgnoreCase(host(), o.host())) != 0) {
        return c;
      }
      if ((c = port - o.port) != 0) {
        return c;
      }
    } else {
      if ((c = compare(authority(), o.authority())) != 0) {
        return c;
      }
    }

    if ((c = compare(path(), o.path())) != 0) {
      return c;
    }
    if ((c = compare(query(), o.query())) != 0) {
      return c;
    }
    return compare(fragment(), o.fragment());
  }

  /**
   * Compares two strings in a case-sensitive way.
   *
   * @param s1 the first string to be compared
   * @param s2 the other string to be compared
   * @return the comparison result.
   */
  private static int compare(String s1, String s2) {
    return Objects.compare(s1, s2, Comparator.nullsFirst(String::compareTo));
  }

  /**
   * Compares two strings in a case-insensitive way.
   *
   * @param s1 the first string to be compared
   * @param s2 the other string to be compared
   * @return the comparison result.
   */
  private static int compareIgnoreCase(String s1, String s2) {
    return Objects.compare(s1, s2, Comparator.nullsFirst(String::compareToIgnoreCase));
  }

  /**
   * Returns a hash code for this URI. This uses the case-normalized decoded
   * string representation to compute the hash code. This calls
   * {@link #toCaseNormalizedString()} to get the decoded string.
   *
   * @return a hash code for this URI.
   * @see #toCaseNormalizedString()
   */
  @Override
  public int hashCode() {
    return toCaseNormalizedString().hashCode();
  }

  /**
   * Returns whether this URI is considered equal to the given URI. This uses
   * the decoded string representations for the comparison. Any schemes and
   * hosts are compared case-insensitively.
   * <p>
   * Note that the path segments are not normalized before comparison.
   * <p>
   * This will return the same result as comparing the string representations
   * from {@link #toCaseNormalizedString()}.
   *
   * @param obj the reference object with which to compare
   * @return whether they're equal.
   * @see #toCaseNormalizedString()
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof URI)) {
      return false;
    }
    URI o = (URI) obj;

    if (!equalsIgnoreCase(scheme(), o.scheme())) {
      return false;
    }

    if (authority != null && o.authority != null) {
      if (!Objects.equals(userInfo(), o.userInfo())) {
        return false;
      }
      if (!equalsIgnoreCase(host(), o.host())) {
        return false;
      }
      if (port != o.port) {
        return false;
      }
    } else {
      if (!Objects.equals(authority(), o.authority())) {
        return false;
      }
    }

    if (!Objects.equals(path(), o.path())) {
      return false;
    }
    if (!Objects.equals(query(), o.query())) {
      return false;
    }
    return Objects.equals(fragment(), o.fragment());
  }

  /**
   * Compares two strings in a case-insensitive way, taking {@code null}s
   * into account.
   *
   * @param s1 the first string to be compared
   * @param s2 the other string to be compared
   * @return the comparison result.
   */
  private static boolean equalsIgnoreCase(String s1, String s2) {
    return (s1 == s2) || (s1 != null && s1.equalsIgnoreCase(s2));
  }

  /**
   * Returns the URI as an encoded string. All characters that need to be
   * percent-encoded will be encoded.
   *
   * @return the URI as an encoded string.
   */
  @Override
  public String toString() {
    if (string == null) {
      // Call the accessor methods so they can decode
      string = toRawString(scheme, authority, path, query, fragment);
    }
    return string;
  }

  /**
   * Returns the URI as a decoded string.
   *
   * @return the URI as a decoded string.
   */
  public String toDecodedString() {
    if (decodedString == null) {
      decodedString = toRawString(scheme(), authority(), path(), query(), fragment());
    }
    return decodedString;
  }

  /**
   * Returns a concatenated string. This does not percent-encode the strings.
   *
   * @return the URI components as a string.
   */
  private static String toRawString(String scheme, String authority,
                                    String path,
                                    String query, String fragment) {
    StringBuilder sb = new StringBuilder();
    if (scheme != null) {
      sb.append(scheme).append(':');
    }
    if (authority != null) {
      sb.append("//").append(authority);
    }
    sb.append(path);
    if (query != null) {
      sb.append('?').append(query);
    }
    if (fragment != null) {
      sb.append('#').append(fragment);
    }
    return sb.toString();
  }

  /**
   * Returns a concatenated string. This percent-encodes the strings.
   *
   * @return the URI components as an encoded string.
   */
  private static String toEncodedString(String scheme, String authority,
                                        String path,
                                        String query, String fragment) {
    StringBuilder sb = new StringBuilder();
    if (scheme != null) {
      sb.append(scheme).append(':');
    }

    if (authority != null) {
      int index = 0;
      sb.append("//");

      // Check for userinfo
      int atIndex = authority.indexOf('@');
      if (atIndex >= 0) {
        sb.append(pctEncode(authority, 0, atIndex, URIParser.USERINFO))
            .append('@');
        index = atIndex + 1;
      }

      // Check for IP-literal
      if (authority.startsWith("[", index)) {
        // Don't encode IP-literals
        sb.append(authority, index, authority.length());
      } else {
        // Not an IP-literal, encode the host part
        int colonIndex = authority.indexOf(':', index);
        if (colonIndex >= 0) {
          sb.append(pctEncode(authority, index, colonIndex, URIParser.REG_NAME))
              .append(authority, colonIndex, authority.length());
        } else {
          sb.append(pctEncode(authority, index, authority.length(), URIParser.REG_NAME));
        }
      }
    }

    if (path != null) {
      sb.append(pctEncode(path, 0, path.length(), URIParser.PATH));
    }
    if (query != null) {
      sb.append('?')
          .append(pctEncode(query, 0, query.length(), URIParser.QUERY_OR_FRAGMENT));
    }
    if (fragment != null) {
      sb.append('#')
          .append(pctEncode(fragment, 0, fragment.length(), URIParser.QUERY_OR_FRAGMENT));
    }
    return sb.toString();
  }

  /**
   * Returns the URI as a normalized string. The string will be decoded.
   * <p>
   * The case of any scheme and host will be normalized to lowercase, but the
   * path segments will not necessarily be normalized.
   *
   * @return a string but with any scheme and host normalized to lowercase.
   */
  public String toCaseNormalizedString() {
    if (caseNormalizedString == null) {
      // Call the accessor methods so they can decode
      StringBuilder sb = new StringBuilder();
      if (scheme != null) {
        sb.append(scheme().toLowerCase()).append(':');
      }
      if (authority != null) {
        sb.append("//");
        if (userInfo != null) {
          sb.append(userInfo());
        }
        sb.append(host().toLowerCase());
        if (port >= 0) {
          sb.append(port());
        }
      }
      sb.append(path());
      if (query != null) {
        sb.append('?').append(query());
      }
      if (fragment != null) {
        sb.append('#').append(fragment());
      }
      caseNormalizedString = sb.toString();
    }
    return caseNormalizedString;
  }
}
