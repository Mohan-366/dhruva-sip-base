FROM engci-maven.cisco.com/webexkubed-docker/wbx3_java_base:2023-01-26_23-50-45
LABEL maintainer="dhruva app team"
LABEL quay.expires-after=7d
ENV JAVA_VERSION=17
ADD dsb-calling-integration-tests.jar /usr/local/dsb-calling-integration-tests.jar
RUN /setenv.sh