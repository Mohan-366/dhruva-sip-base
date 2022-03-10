FROM dockerhub.cisco.com/webexkubed-docker/wbx3_java_base:2021-11-23_22-25-24
LABEL maintainer="dhruva app team"
ENV JAVA_VERSION=11
ADD dsb-calling-app/integration/target/dsb-calling-integration-tests.jar /usr/local/dsb-calling-integration-tests.jar
RUN /setenv.sh