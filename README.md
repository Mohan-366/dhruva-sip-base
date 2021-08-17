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

#### ENV variables to be configured
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
                           	`"transport": "<UDP>",`
                           	`"port": <port>,`
                           	`"recordRoute": true`
                           `}] `
       - Provide Server Groups as below
            - In 'Name' -> `sipServerGroups`
            - In 'Value' ->`[{`
                            `"serverGroupName": "<networkName>",`
                            `"networkName": "<IP of machine where DSB runs",`
                            `"elements": [{"ipAddress": "<SG IP address>", "port": "<SG port>", "transport": "UDP", "qValue": <qValue>, "weight": <weight>}],` 
                            `"sgPolicy": <"PolicyName">,`   	
                            `}] `
                                  
       - Provide Server Groups as below
            - In 'Name' -> `sgPolicies`
            - In 'Value' -> `[{`
                                    `"name": "<networkName>",`
                                    `"lbType": "<IP of machine where DSB runs",`
                                    `"failoverResponseCodes": [{"ipAddress": [List of Error Codes]`  	
                                         `}] `

```yaml 
Reference
  sipServerGroups:
[{"serverGroupName": "SG1", "networkName": "UDPNetwork", "lbType": "call-id", "elements": [{"ipAddress": "127.0.0.1", "port": "5060", "transport": "TLS", "qValue": 0.9, "weight": 0}], "sgPolicy": "sgPolicy"},{"serverGroupName": "SG2", "networkName": "net_me_tcp", "lbType": "call-id", "elements": [{"ipAddress": "127.0.0.2", "port": "5060", "transport": "TLS", "qValue": 0.9, "weight": 0}]}]
  sgPolicies:
[{"name": "policy1", "lbType": "call-type", "failoverResponseCodes": [501,502]},
{"name": "global", "lbType": "call-type", "failoverResponseCodes": [503,504]}]
```
**NOTE**

* For DynamicSG/ Static SG without any SGPolicy configured, default SGPolicy would be ```global```. 
* Make sure while configuring ```sipServerGroups```, ```networkName``` should be one of the ```sipListenPoints``` network name. 
