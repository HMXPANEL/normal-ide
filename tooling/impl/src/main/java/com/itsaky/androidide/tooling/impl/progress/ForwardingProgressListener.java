package com.itsaky.androidide.tooling.impl.progress;

import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardingProgressListener implements ProgressListener {

  private static final Logger LOG = LoggerFactory.getLogger(ForwardingProgressListener.class);

  @Override
  public void statusChanged(ProgressEvent event) {
    LOG.trace("Progress: {}", event);
  }
}
