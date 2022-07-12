FROM dockerhub.cisco.com/webexkubed-docker/wbx3-tomcat:2021-12-21_22-25-26
LABEL maintainer="dhruva app team"
RUN apt-get update -y && apt-get install lsof -y
ADD dsb-calling-app-server-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
