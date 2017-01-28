/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.process.JavaForkOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 *
 * @author akhikhl
 */
class AppBeforeIntegrationTestTask extends AppStartTask {

  private static final Logger log = LoggerFactory.getLogger(AppBeforeIntegrationTestTask)

  private String integrationTestTask_
  private boolean integrationTestTaskAssigned

  AppBeforeIntegrationTestTask() {
    scanInterval = 0 // disable scanning on integration tests
  }
  
  @Override
  protected boolean getDefaultJacocoEnabled() {
    true
  }

  @Override
  protected boolean getIntegrationTest() {
    true
  }

  String getIntegrationTestTask() {
    integrationTestTask_ ?: project.gretty.integrationTestTask
  }

  boolean getIntegrationTestTaskAssigned() {
    integrationTestTaskAssigned
  }

  @Override
  protected boolean getManagedClassReload(ServerConfig sconfig) {
    // disable managed class reloads on integration tests
    false
  }

  void integrationTestTask(String integrationTestTask) {
    if(integrationTestTaskAssigned) {
      log.warn '{}.integrationTestTask is already set to "{}", so "{}" is ignored', name, getIntegrationTestTask(), integrationTestTask
      return
    }
    integrationTestTask_ = integrationTestTask
    def thisTask = this
    project.tasks.all { task ->
      if(task.name == thisTask.integrationTestTask) {
        task.dependsOn thisTask
        thisTask.dependsOn { project.tasks.prepareInplaceWebApp }
        thisTask.dependsOn { project.tasks.testClasses }
        if(task.name != 'test' && project.tasks.findByName('test'))
          thisTask.mustRunAfter { project.tasks.test }
        if(GradleUtils.instanceOf(task, 'org.gradle.process.JavaForkOptions')) {
          task.doFirst {
            if(thisTask.didWork)
              passSystemPropertiesToIntegrationTask(task)
          }
          task.doLast {
            if (thisTask.didWork)
              // UP-TO-DATE checking is done (obviously) immediately before execution.
              // The snapshot of task input, however, is not take until AFTER execution. What this means is that
              // if a task's input changes during execution (e.g. properties are added to it), that task can never be
              // considered UP-TO-DATE. Source: https://discuss.gradle.org/t/when-are-input-output-snapshots-taken
              //
              // That's precisely what we did above in task.doFirst{}. We had to: serverStartInfo isn't populated
              // until the execution phase. So, in order for the UP-TO-DATE machinery to work properly, we must remove
              // the properties we added above, so that task's properties before and after execution will be the same.
              task.systemProperties.keySet().removeAll { it.startsWith("gretty.") }
          }
        }
    
        // We're trying hard to only execute appBeforeIntegrationTest if integrationTestTask will execute.
        // We won't execute if integrationTestTask is disabled--that's simple.
        // We also won't execute if integrationTestTask is considered UP-TO-DATE. That's much harder to determine,
        // and we had to dig into internal Gradle APIs to find that info. Expect this to break in the future.
        // There are still other cases where test execution could be skipped that we don't or can't check
        // (e.g. one of its onlyIf()s returns false), but those situations are rare.
        thisTask.onlyIf {
          if (!task.enabled) {
            return false;
          }
    
          TaskArtifactState state = task.services.get(TaskArtifactStateRepository).getStateFor(task)
          return !state.isUpToDate([])
        }
      }
    }
    integrationTestTaskAssigned = true
  }

  // serverStartInfo gets populated in this task's action() method.
  // Therefore, we can't call this method until this task has run.
  protected void passSystemPropertiesToIntegrationTask(JavaForkOptions task) {

    def host = serverStartInfo.host

    task.systemProperty 'gretty.host', host

    def contextPath
    if(task.hasProperty('contextPath') && task.contextPath != null) {
      contextPath = task.contextPath
      if(!contextPath.startsWith('/'))
        contextPath = '/' + contextPath
    }
    else if(serverStartInfo.contexts)
      contextPath = serverStartInfo.contexts[0].contextPath

    task.systemProperty 'gretty.contextPath', contextPath

    String preferredProtocol
    String preferredBaseURI

    def httpPort = serverStartInfo.httpPort
    String httpBaseURI
    if(httpPort) {
      task.systemProperty 'gretty.port', httpPort
      task.systemProperty 'gretty.httpPort', httpPort
      httpBaseURI = "http://${host}:${httpPort}${contextPath}"
      task.systemProperty 'gretty.baseURI', httpBaseURI
      task.systemProperty 'gretty.httpBaseURI', httpBaseURI
      preferredProtocol = 'http'
      preferredBaseURI = httpBaseURI
    }

    def httpsPort = serverStartInfo.httpsPort
    String httpsBaseURI
    if(httpsPort) {
      task.systemProperty 'gretty.httpsPort', httpsPort
      httpsBaseURI = "https://${host}:${httpsPort}${contextPath}"
      task.systemProperty 'gretty.httpsBaseURI', httpsBaseURI
      preferredProtocol = 'https'
      preferredBaseURI = httpsBaseURI
    }

    if(preferredProtocol)
      task.systemProperty 'gretty.preferredProtocol', preferredProtocol
    if(preferredBaseURI)
      task.systemProperty 'gretty.preferredBaseURI', preferredBaseURI
  }
}
