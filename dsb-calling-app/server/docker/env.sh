#!/bin/bash

echo "BEGIN env.sh"
export JAVA_VERSION=11
export TOMCAT_VERSION=9
export TOMCAT_http_port=8080
export temp=$(ip -4 addr show eth2 2>/dev/null | grep -oP '(?<=inet\s)\d+(.\d+){3}')
if [ ! -z "$temp" ]
then
  POD_IP=$temp
fi
#if [ -f "/test/server.pem" ]; then
#    echo "IMPORTing public cert to cacerts"
#    export JHOME="/usr/lib/jvm/openjdk-8u222-b10-jre"
#    /usr/lib/jvm/openjdk_8/jre/bin/keytool \
#        -import -alias host.server \
#        -keystore /usr/lib/jvm/openjdk_8/jre/lib/security/cacerts \
#        -storepass changeit -noprompt \
#        -file /test/server.pem
#fi
echo "DONE env.sh"
