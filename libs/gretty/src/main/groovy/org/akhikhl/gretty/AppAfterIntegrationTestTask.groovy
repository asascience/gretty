/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class AppAfterIntegrationTestTask extends AppStopTask {

  private static final Logger log = LoggerFactory.getLogger(AppAfterIntegrationTestTask)

  private String integrationTestTask_
  private boolean integrationTestTaskAssigned

  @TaskAction
  void action() {
    super.action()
    if(project.ext.has('grettyLaunchThread') && project.ext.grettyLaunchThread != null) {
      project.ext.grettyLaunchThread.join()
      project.ext.grettyLaunchThread = null
      project.ext.grettyLauncher.afterLaunch()
      project.ext.grettyLauncher.dispose()
      project.ext.grettyLauncher = null
    }
    System.out.println 'Server stopped.'
  }

  String getIntegrationTestTask() {
    integrationTestTask_ ?: project.gretty.integrationTestTask
  }

  boolean getIntegrationTestTaskAssigned() {
    integrationTestTaskAssigned
  }

  void integrationTestTask(String integrationTestTask) {
    if(integrationTestTaskAssigned) {
      log.warn '{}.integrationTestTask is already set to "{}", so "{}" is ignored', name, getIntegrationTestTask(), integrationTestTask
      return
    }
    
    integrationTestTask_ = integrationTestTask
    def thisTask = this
    
    project.tasks.all { t ->
      if(t.name == thisTask.integrationTestTask){
        t.finalizedBy thisTask
        
        Closure instanceOfAppBeforeIntegTest = { GradleUtils.instanceOf(it, AppBeforeIntegrationTestTask.name) }
        Set<Task> appBeforeIntegTasks = t.dependsOn.findAll(instanceOfAppBeforeIntegTest)
        
        // We only need to run this task if one of the associated AppBeforeIntegrationTestTasks did work.
        onlyIf {
          appBeforeIntegTasks.any { it.didWork }
        }
      }
    }
    
    integrationTestTaskAssigned = true
  }
}
