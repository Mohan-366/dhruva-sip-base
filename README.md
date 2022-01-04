# Dhruva-Sip-Base

[![Build Status](https://engci-jenkins-blr.cisco.com/jenkins/buildStatus/icon?job=team_dhruva%2FDSB%2Fmaster)](https://engci-jenkins-blr.cisco.com/jenkins/job/team_dhruva/job/DSB/job/master/)

Dhruva Sip Base(DSB) provides common set of sip functionalities that can be used for building any kind of sip application e.g sip proxy, sip ua 
It uses Jain sip stack underneath.

To use the dsb artifacts in any new repo, the following text will have to be added to pom.xml and settings.xml

    * pom.xml

    <repositories>
        <repository>
            <id>bms-artifactory</id>
            <name>bms-artifactory-releases</name>
            <url>https://engci-maven.cisco.com/artifactory/dhruva-sip-base-snapshot/</url>
        </repository>
    </repositories>


    <dependency>
        <groupId>com.cisco</groupId>
        <artifactId>dhruva-sip-base</artifactId>
        <version>1.0-SNAPSHOT</version>
        <type>pom</type>
    </dependency>


    * settings.xml

    <servers>
	  <server>
	    <id>bms-artifactory</id>
	    <username>[username]</username>
	    <password>[password]</password>
	  </server>
	</servers>

## Getting Started

### Prerequisites
- Ensure you have access to clone this repo.
- Clone the repo in your IDE (IntelliJ Ultimate recommended (see below)) and make sure you have JDK8.
- Setup [secure access to Cisco's artifactory](https://sqbu-github.cisco.com/pages/WebexSquared/docs/DeveloperTools/maven.html).

#### Intellij Ultimate

- Talk to your manager for an IntelliJ Ultimate referral link, and create an account with your Cisco Email address.
- You will receive a confirmation Email.
- Confirm your account in the email, it will take you to jetbrains site to create your account.
- Then you can see your license ID.
- Go to Intellij Ultimate, under Help > Register..., enter your username and password.

### Build/Tests
- `mvn clean verify` at the top level builds and runs all tests.

### Running in Tomcat in Intellij IDE
- Go to Run -> Edit Configurations
- Click on + sign in new window and find Tomcat Server and select Local
- Now click on configure button to the right of 'Application Server' and give a name to your tomcat configuration
- Add path to your local tomcat installation (make sure the version is `> 7.x`)
- Optionally uncheck "After launch" under the "Open browser" section.
- Set the URL as `http://localhost:8080/dsb_calling_app_war_exploded/`
- In VM options field enter: `-Xmx2048m -Xms1024m -DexternalUrlProtocol=http -DjedisPoolHealthCheckMonitorEnabled=false`.
- Now go to the deployment tab
- Use + button to add the artifact `sample-app-war-exploded` (pick one with 'exploded' word in name, it will speedup your tomcat run). You cab deploy any application, just choose the right war file.


#### ENV variables to be configured
   - To set logger level to "Debug", use environment variable `debug_mode` and set the value as `enabled` in the tomcat configuration's 'Startup/Connection' section.


#### application-local.yaml to be configured
   - To configure any ConfigurationProperty Source classes we have to configure it via property source yaml file
   - In bootstrap.yaml set `spring.profiles.active: local` to pick up application-local.yaml as propertySource file
   - To configure listenPoints
     ```yaml
        common:
          listenPoints:
            - name: net_sp
              port: 5060
              transport: UDP
              hostIPAddress: "127.0.0.1"

   - To configure ServerGroup
     ```yaml
        common:
            serverGroups:
              - name: &UsPoolA UsPoolA
                hostName: "UsPoolA"
                networkName: net_sp
                lbType: weight
                elements:
                  - ipAddress: "127.0.0.1"
                    port: 6060
                    transport: UDP
                    priority: 10
                    weight: 100
                 - ipAddress: "127.0.0.1"
                   port: 6061
                   transport: UDP
                   priority: 5
                   weight: 100
                sgPolicy: policy1
                optionsPingPolicy: opPolicy1
                priority: 10
                weight: 100
   - To configure  SG Policy
     ```yaml
       common:
          sgPolicy:
             policy1:
                name: policy1
                failoverResponseCodes:
                  - 501
                  - 502
        
   - To configure OptionPingPolicy
        ```yaml
        optionsPingPolicy:
           opPolicy1:
              name: opPolicy1
              failoverResponseCodes:
                 - 501
                 - 502
                 - 503
             upTimeInterval: 30000
             downTimeInterval: 5000
             pingTimeOut: 500
     
***NOTE:For all the configuration available please check CommonConfigurationProperty class***

###Default config for each application is present in application.yaml, application-local.yaml under the modules resources folder
