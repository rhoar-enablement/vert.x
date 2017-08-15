node("launchpad-maven") {
  checkout scm
  stage("Prepare") {
    sh "oc policy add-role-to-user view -z default"
  }
  stage("Install ConfigMap") {
    sh "if ! oc get configmap app-config -o yaml | grep app-config.yml; then oc create configmap app-config --from-file=app-config.yml; fi"
  }
  stage("Build") {
    sh "mvn fabric8:deploy -Popenshift -DskipTests"
  }
  stage("Deploy")
}
