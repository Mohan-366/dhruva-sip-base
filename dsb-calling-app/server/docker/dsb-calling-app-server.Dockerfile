FROM dockerhub.cisco.com/webexkubed-docker/wbx3-tomcat:2020-11-23_18-36-59
LABEL maintainer="dhruva app team"
RUN apt-get -y update && apt-get install -y redis-server redis-tools && apt-get install -y iproute2
ADD dsb-calling-app/server/docker/env.sh /env.sh
RUN chmod +x /env.sh
ADD dsb-calling-app/server/target/dsb-calling-app-server-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
