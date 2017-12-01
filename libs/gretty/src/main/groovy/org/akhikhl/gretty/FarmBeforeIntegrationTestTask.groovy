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
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.process.JavaForkOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 *
 * @author akhikhl
 */
class FarmBeforeIntegrationTestTask extends FarmStartTask {

  protected static final Logger log = LoggerFactory.getLogger(FarmBeforeIntegrationTestTask)

  private String integrationTestTask_
  private boolean integrationTestTaskAssigned

  FarmBeforeIntegrationTestTask() {
    def thisTask = this
    mustRunAfter {
      List projects = project.rootProject.allprojects as List
      def result = []
      int thisProjectIndex = projects.indexOf(project)
      if(thisProjectIndex > 0)
        result.addAll projects[0..thisProjectIndex - 1].findAll { it.extensions.findByName('farms') }.collectMany { proj ->
          proj.extensions.farms.farmsMap.keySet().collect { proj.tasks['farmAfterIntegrationTest' + it] }
        }
      def farms = project.extensions.farms.farmsMap.keySet() as List
      def thisFarmIndex = farms.indexOf(farmName)
      if(thisFarmIndex > 0)
        result.addAll farms[0..thisFarmIndex - 1].collect { project.tasks['farmAfterIntegrationTest' + it] }
      result = result.findAll { otherTask ->
        thisTask.integrationTestTask != otherTask.integrationTestTask || !thisTask.getIntegrationTestProjects().intersect(otherTask.getIntegrationTestProjects())
      }
      result
    }
    doFirst {
      getIntegrationTestProjects().each { proj ->
        proj.tasks.each { t ->
          if(GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppBeforeIntegrationTestTask') ||
             GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppAfterIntegrationTestTask'))
            if(t.enabled)
              t.enabled = false
        }
      }
      project.tasks.each { t ->
        if(GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppBeforeIntegrationTestTask') ||
            GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppAfterIntegrationTestTask'))
          if(t.enabled)
            t.enabled = false
      }
      project.subprojects.each { proj ->
        proj.tasks.each { t ->
          if(GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppBeforeIntegrationTestTask') ||
             GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppAfterIntegrationTestTask') ||
             GradleUtils.instanceOf(t, 'org.akhikhl.gretty.FarmBeforeIntegrationTestTask') ||
             GradleUtils.instanceOf(t, 'org.akhikhl.gretty.FarmAfterIntegrationTestTask') ||
             GradleUtils.instanceOf(t, 'org.akhikhl.gretty.FarmIntegrationTestTask'))
            if(t.enabled)
              t.enabled = false
        }
      }
    }
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
    integrationTestTask_ ?: new FarmConfigurer(project).getProjectFarm(farmName).integrationTestTask
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
    project.rootProject.allprojects.each { proj ->
      proj.tasks.all { Task task ->
        if(getIntegrationTestProjects().contains(task.project)) {
          if (task.name == thisTask.integrationTestTask) {
            // Add an ordering but not a dependency. It would be inappropriate for integrationTestTask to depend on
            // FarmBeforeIntegrationTestTask because integrationTestTask may not even be part of a farm.
            task.mustRunAfter thisTask
            
            thisTask.mustRunAfter proj.tasks.testClasses
            if (task.name != 'test' && project.tasks.findByName('test'))
              thisTask.mustRunAfter project.tasks.test
            
            if (GradleUtils.instanceOf(task, 'org.gradle.process.JavaForkOptions')) {
              task.doFirst {
                if (thisTask.didWork) {
                  passSystemPropertiesToIntegrationTask(task)
                }
              }
              task.doLast {
                // See first note in AppBeforeIntegrationTestTask.integrationTestTask().
                if (thisTask.didWork) {
                  task.systemProperties.keySet().removeAll { it.startsWith("gretty.") }
                }
              }
            }
  
            // See second note in AppBeforeIntegrationTestTask.integrationTestTask().
            thisTask.onlyIf {
              if (!task.enabled) {
                return false
              }
    
              TaskArtifactState state = task.services.get(TaskArtifactStateRepository).getStateFor(task)
              return !state.isUpToDate([])
            }
          } else if (GradleUtils.instanceOf(task, 'org.akhikhl.gretty.AppBeforeIntegrationTestTask') &&
                     task.integrationTestTask == thisTask.integrationTestTask)
            task.mustRunAfter thisTask // need this to be able to disable AppBeforeIntegrationTestTask in doFirst
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
    else {
      Iterable<WebAppConfig> webAppConfigs = getStartConfig().getWebAppConfigs()
      if(webAppConfigs) {
        WebAppConfig webAppConfig = webAppConfigs.find { it.projectPath == task.project.path }
        if (webAppConfig == null)
          webAppConfig = webAppConfigs.first()
        contextPath = webAppConfig.contextPath
      }
    }
  
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
  
    task.systemProperty 'gretty.farm', (farmName ?: 'default')
  }
}
