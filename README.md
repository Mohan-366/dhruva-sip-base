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


   - Provide listen points for Dhruva SIP Base. Since we are running DSB in Tomcat in IntelliJ (more details in 'Running in Tomcat in Intellij IDE'),
   pass the required config as environment variables.
       - For this, go to 'Services' in IDE. You will find the tomcat that you have configured earlier. Right click on that tomcat server
       and choose 'Edit Configuration'.
       - Choose 'Startup/Connection' section.
       - Enable checkbox '_Pass environment variables_'
       - Provide listen points as below
           - In 'Name' -> `sipListenPoints`
           - In 'Value' -> `[{`
                               `"name": "<networkName>",`
                               `"hostIPAddress": "<IP of machine where DSB runs",`
                               `"transport": "<TCP>",`
                               `"port": <port>,`
                               `"recordRoute": true`
                           `}] `
       - Provide Server Groups as below
            - In 'Name' -> `sipServerGroups`
            - In 'Value' ->`[{`
                            `"serverGroupName": "<SG name>",`
                            `"networkName": "<Network name>",`
                            `"elements": [{"ipAddress": "<SG IP address>", "port": "<SG port>", "transport": "UDP", "qValue": <qValue>, "weight": <weight>}],` 
                            `"sgPolicy": <"PolicyName">,`   	
                            `}] `
       - Provide Dynamic Server Group as below
                   - In 'Name' -> `sipDynamicServerGroups`
                   - In 'Value' -> `[{`
                                   `"serverGroupName": "<SG name>",`
                                   `"sgPolicy": <"PolicyName">`
                                    `}]` 
                                  
       - Provide Server Groups as below
            - In 'Name' -> `sgPolicies`
            - In 'Value' -> `[{`
                                    `"name": "<Policy Name>",`
                                    `"lbType": "<Load balancer type>",`
                                    `"failoverResponseCodes": [{"ipAddress": [List of Error Codes]`  	
                                         `}] `

```yaml 
Reference
  sipServerGroups:
[{"serverGroupName": "SG1", "networkName": "UDPNetwork", "lbType": "call-id", "elements": [{"ipAddress": "127.0.0.1", "port": "5060", "transport": "TLS", "qValue": 0.9, "weight": 0}], "sgPolicy": "sgPolicy"},{"serverGroupName": "SG2", "networkName": "net_me_tcp", "lbType": "call-id", "elements": [{"ipAddress": "127.0.0.2", "port": "5060", "transport": "TLS", "qValue": 0.9, "weight": 0}]}]
dynamicServerGroup:
[{"serverGroupName": "cisco.webex.com","sgPolicy": "policy1"}]
  sgPolicies:
[{"name": "policy1", "lbType": "call-type", "failoverResponseCodes": [501,502]},
{"name": "global", "lbType": "call-type", "failoverResponseCodes": [503,504]}]
```
**NOTE**

* For DynamicSG/ Static SG without any SGPolicy configured, default SGPolicy would be ```global```. 
* Make sure while configuring ```sipServerGroups```, ```networkName``` should be one of the ```sipListenPoints``` network name. 
* Make sure to check the README of the app you are deploying, so that proper env variables are passed specific to that App.

#### Using TLS in DSB

- Add TLS ListenPoint as follows:
    `[{`
                               `"name": "<networkName>",`
                               `"hostIPAddress": "<IP of machine where DSB runs",`
                               `"transport": "TLS",`
                               `"port": <port>,`
                               `"recordRoute": true`
                           `}] `
- Test keystore and certs added
A test keystore.kjs file is present in dsb-common/src/test/resources/ along with server.crt.pem and server.key.pem which have been added to the keystore.jks. These can be used to make TLS sipp calls through the application. 

- Config Changes
    - Please note, to run TLS you will have to specify keystore and truststore location in provide env as follows

      ```-Ddsb.tlsKeyStoreFilePath=/tmp/keystore.jks -Ddsb.tlsKeyStorePassword=dsb123```
      ```-Ddsb.tlsTrustStoreFilePath=/tmp/keystore.jks -Ddsb.tlsTrustStorePassword=dsb123```

      - By default, the TLS authentication type is set to ```SERVER``` type in Jain. If you wish to enable MTLS (server and client authentication) then set the following property in env. 

        dsb.clientAuthType = “Enabled” (by default this is “Disabled”)

      - As a result, the default trustManager will have above config.

    - There are three different types of truststores possible. 

         - SystemTrustStore with MTLS/SERVER authentication enabled as per the above config. 
         - CertTrustManager used to talk to cert service for authentication
         - Permissive TrustStore which allows everything (any certificate).

    - Every stack can choose from one of the above. 
         - In order to choose SystemTrustStore, tlsAuthType in SipListenPoint must not be “NONE”. 
         - The default value for this in properties file is SERVER. And can be overridden in SIPListenPoint json env provided.
         - In order to choose Permissive TrustStore, specify property tlsAuthType as NONE in json as follows:
                           `[{`                    
                               `"name": "<networkName>",`
                               `"hostIPAddress": "<IP of machine where DSB runs",`
                               `"transport": "TLS",`
                               `"port": <port>,`
                               `"recordRoute": true`
                               `"tlsAuthType"`: "NONE"`
                           `}] `
         - In order to get CertTrustManager set property ```dsb.enableCertService``` to true.


