FROM engci-maven.cisco.com/webexkubed-docker/wbx3_tomcat:2023-02-09_17-24-13
LABEL maintainer="dhruva app team"
LABEL quay.expires-after=7d
RUN apt-get update -y && apt-get install lsof -y
ADD env.sh /env.sh
RUN chmod +x /env.sh
ADD dsb-calling-app-server-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
