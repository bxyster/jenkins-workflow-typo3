/**

  JENKINS WORKFLOW FOR CAPISTRANO-TYPO3

  author: Pim Snel

  This script is written with only capistrano-typo3 compatibility in
  mind. It can be modified for other purposes.

  Based on the excellent example script by Mohamed Alsharaf locate here
  https://github.com/satrun77/workflow-script

  Workflow steps in normal order:
  1. stepBootStrap
  2. stepSyncAndDeployCI
  3. stepTestCI
  4. stepDeployProduction
  5. stepTestProduction

  TODO
  - implement submitter for more security
*/

import groovy.json.JsonSlurper

//+---------------- End of pre-conditions

jenkinsConfig = {}

deployers = {}
branchName = 'master'
StageNameCI = 'ci'
StageNameProduction = 'production'
watirGitUrl = ''

interactive = true

//+---------------- End of configurations

def getBuildUserMailAddress() {
  def item = hudson.model.Hudson.instance.getItem(env.JOB_NAME) 
  def build = item.getLastBuild()
  def cause = build.getCause(hudson.model.Cause.UserIdCause.class);
  def id = cause.getUserId()
  User u = User.get(id);
  def umail = u.getProperty(Mailer.UserProperty.class)
  return umail.getAddress()
}



def stepBootStrap() {

  node {
    sh "mkdir -p cap"
    sh "mkdir -p src"

    dir('cap') {

      git url: capGitUrl

      sh "../yml2json.rb jenkins.yml jenkins.json"

      def str = readFile('jenkins.json')
      jenkinsConfig = new JsonSlurper().parseText(str)

      testers = jenkinsConfig['testers']
      developers = jenkinsConfig['developers']
      deployers = jenkinsConfig['deployers']

      def emailTo = getBuildUserMailAddress()

      StageNameCI = jenkinsConfig['stage_name_ci']
      StageNameProduction = jenkinsConfig['stage_name_production']
      watirGitUrl = jenkinsConfig['watir_git_url']
      branchName = 'master'
    }
  }

  if(interactive) {

    def params = []
    def moveOptions = ''
    moveOptions += 'Run full deployment workflow\n'
    moveOptions += 'Single run stepSyncAndDeployCI\n'
    moveOptions += 'Start at stepTestCI\n'
    moveOptions += 'Start at stepDeployProduction\n'
    moveOptions += 'Start at stepTestProduction\n'

    def stepAction = askQuestion('stepBootStrap',params,moveOptions)

    if (stepAction['Move to'] == 'Single run stepSyncAndDeployCI') {
      stepSyncAndDeployCI()
      interactive = false
    }
    else if(stepAction['Move to'] == 'Start at stepTestCI') {
      stepTestCI()
    }
    else if(stepAction['Move to'] == 'Start at stepDeployProduction') {
      stepDeployProduction()
    }
    else if(stepAction['Move to'] == 'Start at stepTestProduction') {
      stepTestProduction()
    }
    else if(stepAction['Move to'] == 'Run full deployment workflow') {
      stepSyncAndDeployCI()
    }
  }
  else {
    stepSyncAndDeployCI()
  }
}

def stepSyncAndDeployCI() {

  stage 'stepSyncAndDeployCI'
  stageAction = true

  while (stageAction) {

    node {
      dir('cap'){
        sh "ls"
        sh "bundle install --binstubs"
        sh "./bin/cap ${StageNameCI} typo3:sync_n_deploy"
      }
    }

    if(interactive) {
      def params = []
      def moveOptions = ''
      moveOptions += 'Continue to stepTestCI\n'
      moveOptions += 'Re-run stepSyncAndDeployCI\n'

      def stepAction = askQuestion('stepSyncAndDeployCI',params,moveOptions)

      if (stepAction['Move to'] == 'Continue to stepTestCI') {
        stageAction = false
        stepTestCI()
        break
      }
      else if(stepAction['Move to'] == 'Re-run stepSyncAndDeployCI') {
        stageAction = false
        stepSyncAndDeployCI()
        break
      }
      else
      {
        stageAction = false
        break
      }
    }
    else {
      stageAction = false
      stepTestCI()
      break
    }
  }
}

def stepTestCI() {
  stage 'stepTestCI'
  stageAction = true

  while (stageAction) {

    if(interactive) {

      //ask which tests to run
      def params = []
      params.add([$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'IE Watir tests', name: 'IE test'])
      params.add([$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Firefox OSX Watir tests', name: 'FFOSX test'])
      params.add([$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Firefox Linux Watir tests', name: 'FFLNX test'])
      params.add([$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'PHP Unit tests', name: 'Unit test'])
      params.add([$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Files syntax check', name: 'Syntax test'])
      def moveOptions = ''
      moveOptions += 'Continue with stepTestCI\n'
      def stepAction = askQuestion('testCiStep',params, moveOptions)

      startTests(stepAction)

      params = []
      moveOptions = ''
      moveOptions += 'Continue to stepDeployProduction\n'
      moveOptions += 'Re-run stepTestCI\n'
      stepAction = askQuestion('testCiStep',params, moveOptions)

      if (stepAction['Move to'] == 'Continue to stepDeployProduction') {
        stageAction = false
        stepDeployProduction()
        break
      }
      else if(stepAction['Move to'] == 'Re-run stepTestCI') {
        stageAction = false
        stepTestCI()
        break
      }
      else
      {
        stageAction = false
        break
      }
    }
    else {
      startWatirTests('FFLNX')
      stageAction = false
      break
    }
  }
}

def stepDeployProduction() {
  node {
    dir('cap'){
      sh "ls"
        sh "bundle install --binstubs"
        //sh "./bin/cap ${StageNameProduction} -T"
        sh "./bin/cap ${StageNameProduction} typo3:deploy"
    }
  }

  stepTestProduction()
}

def stepTestProduction() {
  echo 'stepTestProduction'
  startWatirTests('master','plan_production')
}

def askQuestion(stageType,params,moveOptions) {

    node('master') {
      sh "echo 'Jenkins Input Needed' | mail -s 'Jenkins Input Needed' ${emailTo}"
    }

    params.add([$class: 'hudson.model.ChoiceParameterDefinition', choices: moveOptions, description: 'Next step action', name: 'Move to'])
    def stepAction = input id: '7fd85613e7e068ad4f3bec8e717f2bc8', message: 'What would you like to do in ' + stageType + '?', ok: 'Proceed', parameters: params

    if(stepAction instanceof String) {
        def newAction = ['Move to':stepAction]
        return newAction
    }

    return stepAction
}

def startTests(actions) {

  parallel unitTests: {
    if (actions['Unit test']) {
      startUnitTests(branchName)
    }
  }, filesSyntax: {
    if (actions['Syntax test']) {
      startBehatTests(branchName)
    }
  }, watirIE: {
    if (actions['IE test']) {
//      startWatirTests('IE')
    }
  }, watirFFOSX: {
    if (actions['FFOSX test']) {
      startWatirTests('FFOSX')
    }
  }, watirFXLinux: {
    if (actions['FFLNX test']) {
      startWatirTests('master')
    }
  }
}

def startUnitTests(branchName) {
    echo '[Action] Unit testing...'
}

def startSyntaxTests(branchName) {
    echo '[Action] Checking for syntax errrors...'
}

def startWatirTests(browserType,plan='plan_ci') {

  node(browserType) {
    sh "mkdir -p test"
    dir('test') {
      git url: watirGitUrl

      sh "bundle install --deployment"
      sh "mkdir -p reports"
      sh "mkdir -p screenshots/tmp"
      sh "rm -f reports/*.xml"
      sh "rm -f screenshots/*.png"
      sh "bundle exec rake testlink:"+plan

      step([$class: 'JUnitResultArchiver', testResults: 'reports/*.xml'])
      step([$class: 'ArtifactArchiver', artifacts: 'screenshots/**/*.png', fingerprint: false])
    }
  }
}

return this
