# DSB-Calling-App Integration Tests

Calling Dial-in and Dial-out call-flow based ITs are located here in respective *IT.java files

## Executing ITs:
1) Using Maven:
- Running `mvn clean verify` will run both unit tests & ITs.
- To run only ITs (skip unit tests) - `mvn clean verify -DskipUTs=true`
- To run only unit tests (skip ITs) - `mvn clean test`

2) Using IntelliJ IDE setup:
- Run dhruva-calling-app in Tomcat
Note: Provide all the properties for Dhruva (from https://sqbu-github.cisco.com/SIPEdge/dhruva-sip-base/blob/master/dsb-calling-app/integration/pom.xml#L86), so this setup uses same set of properties as when executed via 'mvn verify'
- Now, run any desired IT 

Dhruva comes-up with a test config that is present in *application-it.yaml* (in dsb-calling-app/server/src/main/resources path)
It is based on this config calls get routed to dhruva-under-test and simulated pstn and wxc clients. 

3) Using dsb-calling-integration-tests.jar
- This can be done in 2 ways.
  - Run 'java -jar -D<any command line arguments to be passed> <path to jar file>' (Make sure that Dhruva is running before executing this - from (2) above)
  - Directly run this Jar artifact from IDE
