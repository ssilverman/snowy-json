/*
 * Created by shawn on 5/17/20 10:47 PM.
 */
package com.qindesign.json.schema.util;

import com.qindesign.json.schema.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Converts a Base64-encoded string into a byte stream.
 */
public class Base64InputStream extends InputStream {
  private final String s;
  private int index;

  private enum State {
    START,
    SECOND,
    THIRD,
    EOF,
  }

  private char[] buf = new char[4];
  private State state;
  private int b1;
  private int b2;
  private int b3;

  /**
   * Creates a new stream. This lets the {@link #read()} method checks that the
   * length is correct.
   *
   * @param s the Base64 string
   */
  public Base64InputStream(String s) {
    Objects.requireNonNull(s, "s");

    this.s = s;
    index = 0;
    state = State.START;
  }

  @Override
  public int read() throws IOException {
    switch (state) {
      case START:
        if (index >= s.length()) {
          state = State.EOF;
          return -1;
        }
        if (index + 4 > s.length()) {
          throw new IOException("Extra bytes");
        }
        s.getChars(index, index + 4, buf, 0);
        index += 4;

        // Check that the high bytes are all zero
        if ((buf[0] | buf[1] | buf[2] | buf[3]) > 0xff) {
          throw new IOException("Bad Base64 characters");
        }
        int b0 = Strings.BASE64_BITS[buf[0]];
        b1 = Strings.BASE64_BITS[buf[1]];
        b2 = Strings.BASE64_BITS[buf[2]];
        b3 = Strings.BASE64_BITS[buf[3]];
        if ((b0 | b1 | b2 | b3) < 0) {
          if (b0 < 0 || b1 < 0) {
            // Allow "====" as a padding
            if (b0 != -2 || b1 != -2 || b2 != -2 || b3 != -2) {
              throw new IOException("Bad Base64 padding");
            }
            state = State.EOF;
            if (index < s.length()) {
              throw new IOException("Extra bytes");
            }
            return -1;
          } else if (b2 < 0) {
            if (b2 == -2) {
              if (b3 != -2) {
                throw new IOException("Bad Base64 padding");
              }
              // 8 bits
              state = State.EOF;
            } else {
              throw new IOException("Bad Base64 padding");
            }
          } else if (b3 != -2) {
            throw new IOException("Bad Base64 padding");
          } else {
            // 16 bits
            state = State.SECOND;
          }
        } else {
          // 24 bits
          state = State.SECOND;
        }
        return (b0 << 2) | (b1 >> 4);

      case SECOND:
        if (b3 == -2) {
          state = State.EOF;
        } else {
          state = State.THIRD;
        }
        return ((b1 << 4) & 0xf0) | (b2 >> 2);

      case THIRD:
        state = State.START;
        return ((b2 << 6) & 0xc0) | b3;

      case EOF:
      default:
        if (index < s.length()) {
          throw new IOException("Extra bytes");
        }
        return -1;
    }
  }
}
