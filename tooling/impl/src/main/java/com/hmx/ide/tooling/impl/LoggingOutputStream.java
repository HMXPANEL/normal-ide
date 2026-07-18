package com.hmx.ide.tooling.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LoggingOutputStream extends OutputStream {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingOutputStream.class);
  private final StringBuilder buffer = new StringBuilder();

  @Override
  public void write(int b) {
    if (b == '\n') {
      flush();
    } else {
      buffer.append((char) b);
    }
  }

  @Override
  public void flush() {
    if (buffer.length() > 0) {
      LOG.info(buffer.toString());
      buffer.setLength(0);
    }
  }
}
