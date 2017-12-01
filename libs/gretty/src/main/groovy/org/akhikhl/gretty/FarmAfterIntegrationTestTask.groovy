/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class FarmAfterIntegrationTestTask extends FarmStopTask {

  private static final Logger log = LoggerFactory.getLogger(FarmBeforeIntegrationTestTask)

  private String integrationTestTask_
  private boolean integrationTestTaskAssigned

  protected final Map webAppRefs = [:]

  // list of projects or project paths
  protected final List integrationTestProjects = []

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

  Iterable<Project> getIntegrationTestProjects() {
    FarmConfigurer configurer = new FarmConfigurer(project)
    Set<Project> result = new LinkedHashSet()
    result.addAll(getWebAppProjects())
    result.addAll(configurer.getIntegrationTestProjects(this.integrationTestProjects + configurer.getProjectFarm(farmName).integrationTestProjects))
    result
  }

  String getIntegrationTestTask() {
    integrationTestTask_ ?: new FarmConfigurer(project).getProjectFarm(farmName).integrationTestTask
  }

  boolean getIntegrationTestTaskAssigned() {
    integrationTestTaskAssigned
  }

  Iterable<Project> getWebAppProjects() {
    FarmConfigurer configurer = new FarmConfigurer(project)
    Map wrefs = [:]
    FarmConfigurer.mergeWebAppRefMaps(wrefs, webAppRefs)
    FarmConfigurer.mergeWebAppRefMaps(wrefs, configurer.getProjectFarm(farmName).webAppRefs)
    if(!wrefs && !configurer.getProjectFarm(farmName).includes)
      wrefs = configurer.getDefaultWebAppRefMap()
    configurer.getWebAppProjects(wrefs)
  }

  void integrationTestProject(Object project) {
    if(project instanceof Project)
      project = project.path
    integrationTestProjects.add(project)
  }

  void integrationTestTask(String integrationTestTask) {
    if(integrationTestTaskAssigned) {
      log.warn '{}.integrationTestTask is already set to "{}", so "{}" is ignored', name, getIntegrationTestTask(), integrationTestTask
      return
    }
    integrationTestTask_ = integrationTestTask
    def thisTask = this
    getIntegrationTestProjects().each { proj ->
      proj.tasks.all { Task task ->
        if(task.name == thisTask.integrationTestTask) {
          thisTask.mustRunAfter task
  
          // In FarmBeforeIntegrationTestTask.integrationTestTask(String), we set the integrationTestTask to run after
          // an instance of FarmBeforeIntegrationTestTask. Here we grab those instances.
          Closure instanceOfFarmBeforeIntegTest = { GradleUtils.instanceOf(it, FarmBeforeIntegrationTestTask.name) }
          Set<Task> farmBeforeIntegTasks = task.mustRunAfter.values.findAll(instanceOfFarmBeforeIntegTest)
  
          assert farmBeforeIntegTasks.size() > 0 :
                  "'${thisTask.integrationTestTask}' was not associated with any FarmBeforeIntegrationTestTasks."
  
          // We only need to run this task if one of the associated FarmBeforeIntegrationTestTasks did work.
          thisTask.onlyIf {
            farmBeforeIntegTasks.any { it.didWork }
          }
        } else if(GradleUtils.instanceOf(task, 'org.akhikhl.gretty.AppAfterIntegrationTestTask') &&
                  task.integrationTestTask == thisTask.integrationTestTask) {
          thisTask.mustRunAfter task
        }
      }
    }
    
    integrationTestTaskAssigned = true
  }

  void webapp(Map options = [:], w) {
    if(w instanceof Project)
      w = w.path
    webAppRefs[w] = options
  }
}
