# Dhruva-Sip-Base

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

