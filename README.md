# Dhruva-Sip-Base

## Wiki link:
https://confluence-eng-gpk2.cisco.com/conf/display/TMEI/DHRUVA+-+Next+Gen+SIP+Edge


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

_Note:_ 
We are in CSB 3.2.4-rc.85 version (in which we can disable the redis dependency for tests)
1. So, you can either have a redis instance running locally in your dev machine (or)
2. Include the following VM options additionally (which disables the dependency on redis)

` -DenableLettuceRedisDataSourceForUserToGroups=false
  -DenableLettuceRedisDataSourceForUserCache=false
  -DenableLettuceRedisDataSourceForOrgCache=false
  -DenableLettuceRedisDataSourceForAuthCache=false`

- Now go to the deployment tab
- Use + button to add the artifact `sample-app-war-exploded` (pick one with 'exploded' word in name, it will speedup your tomcat run). You can deploy any application, just choose the right war file.


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
                    priority: 10
                    weight: 100
                 - ipAddress: "127.0.0.1"
                   port: 6061
                   priority: 5
                   weight: 100
                routePolicy: policy1
                pingOn: true
   - To configure  ROUTE Policy, used by ServerGroup & Trunk
     ```yaml
       common:
          routePolicy:
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
     
***NOTE:For all the configuration available please check CommonConfigurationProperty class***

### Default config for each application is present in application.yaml, application-local.yaml under the modules resources folder

#### Using TLS in DSB


- Add TLS ListenPoint as per the following:
     ```yaml
        common:
          listenPoints:
            - name: <network_name>
              port: <port>
              transport: TLS
              hostIPAddress: "<IP of machine where DSB runs"
              recordRoute: true
- Test keystore and certs added
  A test keystore.kjs file is present in dsb-common/src/test/resources/ along with server.crt.pem and server.key.pem which have been added to the keystore.jks. These can be used to make TLS sipp calls through the application.

- sipp commands with tls certs

    - UAS : sipp -sf uas.xml -p <uas listen port>  -i <uas ip> -t l1 -tls_cert server.crt.pem -tls_key server.key.pem
    - UAC : sipp -sf uac.xml -i <uac ip> -p <uas port> <dsb ip:dsb tls port> -t l1 -tls_cert server.crt -tls_key server.key  -m 1

- Config Changes

    - Please note, to run TLS you will have to specify keystore and truststore location in the configuration YAML file, under "common" tag
      ```yaml
          common:
            tlsKeyStorePassword: dsb123
            tlsKeyStoreType: "jks"
            tlsKeyStoreFilePath: "/tmp/keystore.jks"
            tlsTrustStorePassword: dsb123
            tlsTrustStoreFilePath: "/tmp/keystore.jks" 
            tlsTrustStoreType: "jks"
    - By default, the TLS authentication type is set to ```SERVER``` type in Jain. If you wish to enable MTLS (server and client authentication) then set the following property in env.
          clientAuthType = “Enabled” (by default this is “Disabled”)
      - As a result, the default trustManager will have above config.
      - There are three different types of truststores possible.
          - SystemTrustStore with MTLS/SERVER authentication enabled as per the above config.
          - CertTrustManager used to talk to cert service for authentication
          - Permissive TrustStore which allows everything (any certificate).
      - Every stack can choose from one of the above.
          - In order to choose SystemTrustStore, tlsAuthType in SipListenPoint must not be “NONE”.
          - The default value for this in properties file is SERVER. And can be overridden in SIPListenPoint json env provided.
          - In order to choose Permissive TrustStore, specify property tlsAuthType as NONE in the configuration YAML file
          ```yaml
          common:
          listenPoints:
            - name: <network_name>
              port: <port>
              transport: TLS
              hostIPAddress: "<IP of machine where DSB runs"
              recordRoute: true
              tlsAuthType: NONE
      - In order to get CertTrustManager set property ```enableCertService``` to true in the configuration YAML file.


### LMA :
- Metrics:
    - micrometer based metrics configurations
    ``` yaml
    management:
      metrics:
        export:
          default-registry:
            enabled: true # to enable default meter reigstry that pushes aggregated metrics to in cluster kafka 
            enabled-extra-logs-for-debugging: false #enable if extra logging for micrometer based metrics is required
            field-to-tag-conversion: # default registry converts all micrometer tags to influx field by default, to exclude certain tags use this config
              tags:
                - action
                - cause
                - reactor.scheduler.id 
          influx:
            enabled: true # only enable for local testing of micrometer based metrics otherwise make it false
            db: "metrics2" # testing locally metrics will be pushed to the db set here in local influx instance
            
    ```
