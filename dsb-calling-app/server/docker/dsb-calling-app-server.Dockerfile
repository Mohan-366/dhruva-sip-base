FROM dockerhub.cisco.com/webexkubed-docker/wbx3-tomcat:2022-11-03_16-52-29
LABEL maintainer="dhruva app team"
LABEL quay.expires-after=7d
RUN apt-get update -y && apt-get install lsof -y
ADD env.sh /env.sh
RUN chmod +x /env.sh
ADD dsb-calling-app-server-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
