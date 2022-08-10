# DSB-Calling-App Integration Tests

Calling Dial-in and Dial-out call-flow based ITs are located here in respective *IT.java files

## Executing ITs:
1) Using Maven:
- Running `mvn clean verify` will run both UTs & ITs.
- To skip UTs (run only ITs) - `mvn clean verify -DskipUTs`
- To skip ITs (run only UTs) - `mvn clean test` (or) `mvn clean verify -DskipITs`
- To skip both UTs and ITs - `mvn clean verify -DskipUTs -DskipITs`

2) Using IntelliJ IDE setup:
- Run dhruva-calling-app in Tomcat
Note: Provide all the properties for Dhruva (from https://sqbu-github.cisco.com/SIPEdge/dhruva-sip-base/blob/master/dsb-calling-app/integration/pom.xml#L86), so this setup uses same set of properties as when executed via 'mvn verify'
- In 'Run Configuration' of the test you are running, add the property `-DdhruvaPublicUrl=http://localhost:8080/dsb_calling_app_server_war_exploded/api/v1`  
*Note:* http://localhost:8080/dsb_calling_app_server_war_exploded -> should be the tomcat url 
- Now, run any desired IT 

Dhruva comes-up with a test config that is present in ***application-it.yaml*** (in dsb-calling-app/server/src/main/resources path)  
It is based on this config calls get routed to dhruva-under-test and simulated pstn and wxc clients.