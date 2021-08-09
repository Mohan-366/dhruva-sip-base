FROM dockerhub.cisco.com/webexkubed-docker/wbx3-tomcat:2020-10-05_16-04-10
LABEL maintainer="dhruva app team"
ADD dhruva-app/docker/env.sh /env.sh
ADD dhruva-app/target/dhruva-app-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
