/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.gradle.api.internal.TaskInternal
import org.gradle.process.JavaForkOptions
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/**
 * Adds Jacoco support to AppBeforeIntegrationTestTask and FarmBeforeIntegrationTestTask.
 *
 * @author akhikhl
 * @author cwardgar
 */
class JacocoHelper implements TaskInternal, JavaForkOptions {
  @Delegate
  private final TaskInternal task

  @Delegate
  private final JavaForkOptions javaForkOptions

  JacocoHelper(TaskInternal task) {
    this.task = task
    
    // This is a FAKE. It's an empty map masquerading as a JavaForkOptions.
    // Any method call that gets delegated to it will result in an UnsupportedOperationException.
    // So, we have to provide real implementations of the JavaForkOptions methods that will actually be called by
    // JacocoPluginExtension. Those are getWorkingDir() and jvmArgs(Object...).
    this.javaForkOptions = [:] as JavaForkOptions

    // The signature of JacocoPluginExtension.applyTo() is:
    //   public <T extends Task & JavaForkOptions> void applyTo(final T task);
    // Looking at the implementation, it actually requires a TaskInternal, not merely a Task.
    // So, this class implements both TaskInternal and JavaForkOptions so that it can be passed to that method.
    task.project.jacoco.applyTo(this)
  }
  
  @Override
  File getWorkingDir() {
    task.project.projectDir
  }
  
  @Override
  JavaForkOptions jvmArgs(Object... arguments) {
    // Do nothing with these. Instead, in Gretty, we set the JVM arguments on a ServerConfig object in
    // StartBaseTask.doPrepareServerConfig().
  }
  
  JacocoTaskExtension getJacoco() {
    extensions.jacoco  // Created in the constructor.
  }
}
