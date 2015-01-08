def flow
node {
    git url: "https://github.com/mipmip/jenkins-workflow-typo3.git"
    flow = load "jenkins.groovy"
}
flow.capGitUrl  = "git/url/with/capistrano/repo.git"
flow.stepBootStrap()
