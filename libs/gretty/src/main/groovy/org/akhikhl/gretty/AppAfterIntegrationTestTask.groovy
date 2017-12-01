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
    
    project.tasks.all { Task task ->
      if(task.name == thisTask.integrationTestTask){
        task.finalizedBy thisTask
        
        // In AppBeforeIntegrationTestTask.integrationTestTask(String), we set the integrationTestTask to depend on
        // an instance of AppBeforeIntegrationTestTask. Here we grab that instance.
        Closure instanceOfAppBeforeIntegTest = { GradleUtils.instanceOf(it, AppBeforeIntegrationTestTask.name) }
        Set<Task> appBeforeIntegTasks = task.dependsOn.findAll(instanceOfAppBeforeIntegTest)
        
        assert appBeforeIntegTasks.size() == 1 : "'${thisTask.integrationTestTask}' ought to be associated with " +
            "exactly one AppBeforeIntegrationTestTask, but instead it was associated with: $appBeforeIntegTasks"
        
        // We only need to run this task if the associated AppBeforeIntegrationTestTask did work.
        thisTask.onlyIf {
          appBeforeIntegTasks.first().didWork
        }
      }
    }
    
    integrationTestTaskAssigned = true
  }
}
