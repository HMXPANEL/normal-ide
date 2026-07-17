package com.itsaky.androidide.tooling.impl.progress;

import com.itsaky.androidide.tooling.api.IToolingApiClient;
import com.itsaky.androidide.tooling.impl.Main;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;

public class ForwardingProgressListener implements ProgressListener {

  @Override
  public void statusChanged(ProgressEvent event) {
    final IToolingApiClient client = Main.client;
    if (client != null) {
      client.onProgressEvent(event);
    }
  }
}
