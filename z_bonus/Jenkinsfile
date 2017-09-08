node("launchpad-maven") {
  checkout scm
  stage("Deploy database") {
    sh "if ! oc get service my-database | grep my-database; then oc new-app -e POSTGRESQL_USER=luke -ePOSTGRESQL_PASSWORD=secret -ePOSTGRESQL_DATABASE=my_data openshift/postgresql-92-centos7 --name=my-database; fi"
  }
  stage("Build") {
    sh "mvn fabric8:deploy -Popenshift -DskipTests"
  }
  stage("Deploy")
}
