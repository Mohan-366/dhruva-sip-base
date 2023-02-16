FROM dockerhub.cisco.com/webexkubed-docker/wbx3_java_base:2021-11-23_22-25-24
LABEL maintainer="dhruva app team"
LABEL quay.expires-after=7d
ENV JAVA_VERSION=17
ADD dsb-calling-integration-tests.jar /usr/local/dsb-calling-integration-tests.jar
RUN /setenv.sh