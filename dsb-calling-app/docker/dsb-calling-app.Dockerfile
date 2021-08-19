FROM dockerhub.cisco.com/webexkubed-docker/wbx3-tomcat:2020-11-23_18-36-59
LABEL maintainer="dhruva app team"
ADD dsb-calling-app/docker/env.sh /env.sh
ADD dsb-calling-app/target/dsb-calling-app-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
